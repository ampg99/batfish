package org.batfish.grammar;

import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.batfish.common.DebugBatfishException;
import org.batfish.common.util.CommonUtil;

public class BatfishParserErrorListener extends BatfishGrammarErrorListener {

   public BatfishParserErrorListener(String grammarName,
         BatfishCombinedParser<?, ?> parser) {
      super(grammarName, parser);
   }

   private String printToken(Token token) {
      int modeAsInt = _combinedParser.getTokenMode(token);
      String mode = _combinedParser.getLexer().getModeNames()[modeAsInt];
      String rawTokenText = token.getText();
      String tokenText = CommonUtil.escape(rawTokenText);
      int tokenType = token.getType();
      String channel = token.getChannel() == Lexer.HIDDEN ? "(HIDDEN) " : "";
      String tokenName;
      int line = token.getLine();
      int col = token.getCharPositionInLine();
      if (tokenType == -1) {
         tokenName = "EOF";
      }
      else {
         tokenName = _combinedParser.getParser().getVocabulary()
               .getSymbolicName(tokenType);
         tokenText = "'" + tokenText + "'";
      }
      return " line " + line + ":" + col + " " + channel + " " + tokenName + ":"
            + tokenText + "  <== mode:" + mode;
   }

   public void syntaxError(ParserRuleContext ctx, Object offendingSymbol,
         int line, int charPositionInLine, String msg) {
      if (_syntaxErrorHandler != null && _syntaxErrorHandler.handle(ctx,
            offendingSymbol, line, charPositionInLine, msg)) {
         return;
      }
      BatfishParser parser = _combinedParser.getParser();
      List<String> ruleNames = Arrays.asList(parser.getRuleNames());
      String ruleStack = ctx.toString(ruleNames);
      List<Token> tokens = _combinedParser.getTokens().getTokens();
      int startTokenIndex = parser.getInputStream().index();
      int lookbackIndex = Math.max(0,
            startTokenIndex - _settings.getMaxParserContextTokens());
      int endTokenIndex = tokens.size();
      StringBuilder sb = new StringBuilder();
      sb.append("parser: " + _grammarName + ": line " + line + ":"
            + charPositionInLine + ": " + msg + "\n");
      Token offendingToken = (Token) offendingSymbol;
      String offendingTokenText = printToken(offendingToken);
      sb.append("Offending Token: " + offendingTokenText + "\n");
      sb.append("Error parsing top (leftmost) parser rule in stack: '"
            + ruleStack + "'.\n");
      String ctxParseTree = ParseTreePrettyPrinter.print(ctx, _combinedParser);
      sb.append("Parse tree of current rule:\n" + ctxParseTree + "\n");
      sb.append("Unconsumed tokens:\n");
      for (int i = startTokenIndex; i < endTokenIndex; i++) {
         Token token = tokens.get(i);
         String tokenText = printToken(token);
         sb.append(tokenText + "\n");
      }
      if (lookbackIndex < startTokenIndex) {
         int numLookbackTokens = startTokenIndex - lookbackIndex;
         sb.append("Previous " + numLookbackTokens + " tokens:\n");
         for (int i = lookbackIndex; i < startTokenIndex; i++) {
            Token lookbackToken = tokens.get(i);
            String tokenText = printToken(lookbackToken);
            sb.append(tokenText + "\n");
         }
      }
      if (offendingToken.getType() == Token.EOF) {
         sb.append("Lexer mode at EOF: " + _combinedParser.getLexer().getMode()
               + "\n");
      }
      String stateInfo = parser.getStateInfo();
      if (stateInfo != null) {
         sb.append("Parser state info:\n" + stateInfo + "\n");
      }

      // collect context from text
      String text = _combinedParser.getInput();
      String[] lines = text.split("\n", -1);
      int errorLineIndex = offendingToken.getLine() - 1;
      int errorContextStartLine = Math
            .max(errorLineIndex - _settings.getMaxParserContextLines(), 0);
      int errorContextEndLine = Math.min(
            errorLineIndex + _settings.getMaxParserContextLines(),
            lines.length - 1);
      sb.append("Error context lines:\n");
      for (int i = errorContextStartLine; i < errorLineIndex; i++) {
         sb.append(String.format("%-11s%s\n", "   " + (i + 1) + ":", lines[i]));
      }
      sb.append(String.format("%-11s%s\n", ">>>" + (errorLineIndex + 1) + ":",
            lines[errorLineIndex]));
      for (int i = errorLineIndex + 1; i <= errorContextEndLine; i++) {
         sb.append(String.format("%-11s%s\n", "   " + (i + 1) + ":", lines[i]));
      }

      String error = sb.toString();
      if (_settings.getThrowOnParserError()) {
         throw new DebugBatfishException("\n" + error);
      }
      else {
         _combinedParser.getErrors().add(error);
      }
   }

   @Override
   public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
         int line, int charPositionInLine, String msg, RecognitionException e) {
      BatfishParser parser = _combinedParser.getParser();
      syntaxError(parser.getContext(), offendingSymbol, line,
            charPositionInLine, msg);
   }

}
