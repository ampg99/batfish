package org.batfish.smt;


import com.microsoft.z3.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertyAdder {

    private Encoder _encoder;

    public PropertyAdder(Encoder encoder) {
        _encoder = encoder;
    }

    public static BoolExpr allEqual(Context ctx, List<Expr> exprs) {
        BoolExpr acc = ctx.mkBool(true);
        if (exprs.size() > 1) {
            for (int i = 0; i < exprs.size() - 1; i++) {
                Expr x = exprs.get(i);
                Expr y = exprs.get(i + 1);
                acc = ctx.mkAnd(acc, ctx.mkEq(x, y));
            }
        }
        return acc;
    }

    public Map<String, BoolExpr> instrumentReachability(GraphEdge ge) {
        Context ctx = _encoder.getCtx();
        Solver solver = _encoder.getSolver();

        BoolExpr fwdIface = _encoder.getForwardsAcross().get(ge.getRouter(), ge);

        Map<String, BoolExpr> reachableVars = new HashMap<>();
        _encoder.getGraph().getConfigurations().forEach((router, conf) -> {
            BoolExpr var = ctx.mkBoolConst("reachable_" + router);
            reachableVars.put(router, var);
            _encoder.getAllVariables().add(var);
        });


        BoolExpr baseRouterReachable = reachableVars.get(ge.getRouter());
        solver.add(ctx.mkEq(fwdIface, baseRouterReachable));

        _encoder.getGraph().getEdgeMap().forEach((router, edges) -> {
            if (!router.equals(ge.getRouter())) {
                BoolExpr var = reachableVars.get(router);
                BoolExpr acc = ctx.mkBool(false);
                for (GraphEdge edge : edges) {
                    BoolExpr fwd = _encoder.getForwardsAcross().get(router, edge);
                    if (edge.getPeer() != null) {
                        BoolExpr peerReachable = reachableVars.get(edge.getPeer());
                        acc = ctx.mkOr(acc, ctx.mkAnd(fwd, peerReachable));
                    }
                }
                solver.add(ctx.mkEq(var, acc));
            }
        });

        // Reachable implies permitted
        /* enc.getGraph().getConfigurations().forEach((router, conf) -> {
            BoolExpr reach = reachableVars.get(router);
            BoolExpr permit = enc.getBestNeighbor().get(router).getPermitted();
            solver.add(ctx.mkImplies(reach, permit));
        }); */

        return reachableVars;
    }

    public Map<String, ArithExpr> instrumentPathLength(GraphEdge ge) {
        Context ctx = _encoder.getCtx();
        Solver solver = _encoder.getSolver();

        BoolExpr fwdIface = _encoder.getForwardsAcross().get(ge.getRouter(), ge);

        Map<String, ArithExpr> lenVars = new HashMap<>();
        _encoder.getGraph().getConfigurations().forEach((router, conf) -> {
            ArithExpr var = ctx.mkIntConst("path-length_" + router);
            lenVars.put(router, var);
            _encoder.getAllVariables().add(var);
        });

        // Lower bound for all lengths
        lenVars.forEach((name, var) -> {
            solver.add(ctx.mkGe(var, ctx.mkInt(-1)));
        });

        // Root router has length 0 if it uses the interface
        ArithExpr zero = ctx.mkInt(0);
        ArithExpr baseRouterLen = lenVars.get(ge.getRouter());
        solver.add(ctx.mkImplies(fwdIface, ctx.mkEq(baseRouterLen, zero)));

        // If no peer has a path, then I don't have a path
        // Otherwise I choose 1 + somePeer value to capture all possible lengths
        _encoder.getGraph().getEdgeMap().forEach((router, edges) -> {
            BoolExpr accNone = ctx.mkBool(true);
            BoolExpr accSome = ctx.mkBool(false);
            ArithExpr x = lenVars.get(router);
            if (!router.equals(ge.getRouter())) {
                for (GraphEdge edge : edges) {
                    if (edge.getPeer() != null) {
                        BoolExpr dataFwd = _encoder.getForwardsAcross().get(router, edge);

                        ArithExpr y = lenVars.get(edge.getPeer());
                        accNone = ctx.mkAnd(accNone, ctx.mkOr(ctx.mkLt(y, zero), ctx.mkNot
                                (dataFwd)));

                        ArithExpr newVal = ctx.mkAdd(y, ctx.mkInt(1));
                        BoolExpr fwd = ctx.mkAnd(ctx.mkGe(y, zero), dataFwd, ctx.mkEq(x, newVal));
                        accSome = ctx.mkOr(accSome, fwd);
                    }
                }
                solver.add(ctx.mkImplies(accNone, ctx.mkEq(x, ctx.mkInt(-1))));
                solver.add(ctx.mkImplies(ctx.mkNot(accNone), accSome));
            }
        });

        return lenVars;
    }

    public Map<String, ArithExpr> instrumentLoad(GraphEdge ge) {
        Context ctx = _encoder.getCtx();
        Solver solver = _encoder.getSolver();

        BoolExpr fwdIface = _encoder.getForwardsAcross().get(ge.getRouter(), ge);

        Map<String, ArithExpr> loadVars = new HashMap<>();
        _encoder.getGraph().getConfigurations().forEach((router, conf) -> {
            ArithExpr var = ctx.mkIntConst("load_" + router);
            loadVars.put(router, var);
            _encoder.getAllVariables().add(var);
        });

        // Lower bound for all lengths
        loadVars.forEach((name, var) -> {
            solver.add(ctx.mkGe(var, ctx.mkInt(0)));
        });

        // Root router has load 1 if it uses the interface
        ArithExpr zero = ctx.mkInt(0);
        ArithExpr one = ctx.mkInt(1);
        ArithExpr baseRouterLoad = loadVars.get(ge.getRouter());
        solver.add(ctx.mkImplies(fwdIface, ctx.mkEq(baseRouterLoad, one)));

        _encoder.getGraph().getEdgeMap().forEach((router, edges) -> {
            if (!router.equals(ge.getRouter())) {
                ArithExpr var = loadVars.get(router);
                ArithExpr acc = ctx.mkInt(0);
                for (GraphEdge edge : edges) {
                    BoolExpr fwd = _encoder.getForwardsAcross().get(router, edge);
                    if (edge.getPeer() != null) {
                        ArithExpr peerLoad = loadVars.get(edge.getPeer());
                        ArithExpr x = (ArithExpr) ctx.mkITE(fwd, peerLoad, zero);
                        acc = ctx.mkAdd(acc, x);
                    }
                }
                solver.add(ctx.mkEq(var, acc));
            }
        });

        return loadVars;
    }

    public BoolExpr instrumentLoop(String router) {
        Context ctx = _encoder.getCtx();
        Solver solver = _encoder.getSolver();

        // Add on-loop variables to track a loop
        Map<String, BoolExpr> onLoop = new HashMap<>();
        _encoder.getGraph().getConfigurations().forEach((r, conf) -> {
            BoolExpr var = ctx.mkBoolConst("on-loop_" + router + "_" + r);
            onLoop.put(r, var);
            _encoder.getAllVariables().add(var);
        });

        // Transitive closure for other routers
        _encoder.getGraph().getEdgeMap().forEach((r, edges) -> {
            BoolExpr var = onLoop.get(r);
            BoolExpr acc = ctx.mkBool(false);
            for (GraphEdge edge : edges) {
                BoolExpr fwd = _encoder.getForwardsAcross().get(r, edge);
                String peer = edge.getPeer();
                if (peer != null) {
                    // If next hop is static route router, then on loop
                    if (peer.equals(router)) {
                        acc = ctx.mkOr(acc, fwd);
                    }
                    // Otherwise check if next hop also is on the loop
                    else {
                        BoolExpr peerOnLoop = onLoop.get(peer);
                        acc = ctx.mkOr(acc, ctx.mkAnd(fwd, peerOnLoop));
                    }
                }
            }
            solver.add(ctx.mkEq(var, acc));
        });

        return onLoop.get(router);
    }


}
