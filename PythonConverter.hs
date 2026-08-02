{-# LANGUAGE MultilineStrings #-}
{-# LANGUAGE RecordWildCards #-}
{-# OPTIONS_GHC -Wall #-}
import Data.Char (isUpper, toLower, toUpper)
import Data.List (intercalate)
import Data.List.Extra (dropEnd)
import Data.Maybe (catMaybes, fromMaybe)
import Text.Parsec
import Text.Parsec.Prim
import Control.Monad.Combinators.Expr (Operator(..), makeExprParser)
import qualified Text.Parsec.Token as P
import Test.Hspec

-- can't use haskellDef with parsec-free
javaStyle :: Monad m => P.GenLanguageDef String st m
javaStyle   = P.LanguageDef
                { P.commentStart   = "/*"
                , P.commentEnd     = "*/"
                , P.commentLine    = "//"
                , P.nestedComments = True
                , P.identStart     = letter
                , P.identLetter    = alphaNum <|> oneOf "_'"
                , P.opStart = oneOf "" -- I added opStart and opLetter to silence warnings
                , P.opLetter = oneOf ""
                , P.reservedNames  = []
                , P.reservedOpNames= []
                , P.caseSensitive  = False
                }

lexer :: Monad m => P.GenTokenParser String u m
lexer       = P.makeTokenParser javaStyle

parens :: Monad m => ParsecT String u m a -> ParsecT String u m a
parens = P.parens lexer

braces :: Monad m => ParsecT String u m a -> ParsecT String u m a
braces = P.braces lexer

stringLiteral :: Monad m => ParsecT String u m String
stringLiteral = P.stringLiteral lexer

reserved :: Monad m => String -> ParsecT String u m ()
reserved = P.reserved lexer

reservedOp :: Monad m => String -> ParsecT String u m ()
reservedOp = P.reservedOp lexer

symbol :: Monad m => String -> ParsecT String u m String
symbol = P.symbol lexer

identifier :: Monad m => ParsecT String u m String
identifier = P.identifier lexer

integer :: Monad m => ParsecT String u m Integer
integer = P.integer lexer

data JavaExpr =
    Num Int
    | None
    | FunRef String
    | FunCall String [JavaExpr]
    | Tuple [JavaExpr]
    | Var String
    | GetItem String JavaExpr
    | InvokeMethod JavaExpr String [JavaExpr]
    | InvokeFunction String [JavaExpr]
    | Ternary JavaExpr JavaExpr JavaExpr
    | And JavaExpr JavaExpr
    | IsTrue String
    | IsFalse String
    | Equals JavaExpr JavaExpr
    | True'
    | False'
    | GetAttribute String String
    deriving (Eq, Ord, Show)

data RangeArgs = MkRangeArgs { start :: Maybe JavaExpr, stop :: JavaExpr, step :: Maybe JavaExpr }
    deriving (Eq, Ord, Show)

emptyRangeArgs :: RangeArgs
emptyRangeArgs = MkRangeArgs { start = Nothing, stop = Num 0, step = Nothing }

data JavaStmt =
    VarDecl String JavaExpr
    | Assign String JavaExpr
    | SetItem String JavaExpr JavaExpr
    | RangeFor JavaExpr RangeArgs [JavaStmt] -- loop_var, range_arg, body
    | While JavaExpr [JavaStmt] -- condition, body
    | Continue
    | Break
    | If JavaExpr [JavaStmt] -- condition, body
    | ExprAsStmt JavaExpr
    deriving (Eq, Ord, Show)

-- engineCall :: Monad m => ParsecT String u m JavaExpr
-- engineCall = do
--     reserved "e."
--     num <|> none <|> funRef <|> tuple <|> invokeMethod <|> invokeFunction



num :: Monad m => ParsecT String u m JavaExpr
num = (do
    x <- reserved "e.fromJava" *> parens integer
    pure $ Num . fromInteger $ x) <?> "num"

none :: Monad m => ParsecT String u m JavaExpr
none = (reserved "e.getNone()" *> pure None) <?> "none"

funCall :: Monad m => ParsecT String u m JavaExpr
funCall = (do
    x <- identifier
    reserved ".call"
    exprs <- parens (sepBy javaExpr (symbol ","))
    pure $ FunCall x exprs) <?> "funCall"

isTrue :: Monad m => ParsecT String u m JavaExpr
isTrue = (do
    x <- identifier
    reserved ".isTrue()"
    pure $ IsTrue x) <?> "isTrue"

isFalse :: Monad m => ParsecT String u m JavaExpr
isFalse = (do
    x <- identifier
    reserved ".isFalse()"
    pure $ IsFalse x) <?> "isFalse"

equals :: Monad m => ParsecT String u m JavaExpr
equals = (do
    x <- identifier
    reserved ".equals"
    y <- parens javaExpr
    pure $ Equals (Var x) y) <?> "equals"

true' :: Monad m => ParsecT String u m JavaExpr
true' = (do
    reserved "e.getTrue()"
    pure True') <?> "true"

false' :: Monad m => ParsecT String u m JavaExpr
false' = (do
    reserved "e.getFalse()"
    pure False') <?> "false"

getAttribute :: Monad m => ParsecT String u m JavaExpr
getAttribute = (do
    n <- identifier
    reserved ".getAttribute"
    attr <- parens stringLiteral
    pure $ GetAttribute n attr) <?> "getAttribute"

tuple :: Monad m => ParsecT String u m JavaExpr
tuple = (do
    reserved "e.newPyTuple"
    exprs <- parens (sepBy javaExpr (symbol ","))
    pure $ Tuple exprs) <?> "tuple"

varOrGetItem :: Monad m => ParsecT String u m JavaExpr
varOrGetItem = (do
    x <- identifier
    rest <- optionMaybe getItem
    (case rest of
        Nothing -> pure (Var x)
        Just arg -> pure (GetItem x arg))) <?> "varOrGetItem" where

    getItem :: Monad m => ParsecT String u m JavaExpr
    getItem = (do
        reserved ".getItem"
        arg <- parens javaExpr
        pure $ arg) <?> "getItem"

table :: Monad m => [[Operator (ParsecT String u m) JavaExpr]]
table = [
        [binaryL "==" Equals]
        , [binaryL "&&" And]
        , [ternary "?" ":" Ternary]
        ]

ternary :: Monad m => String -> String -> (a -> a -> a -> a) -> Operator (ParsecT String u m) a
ternary name0 name1 fun = TernR ((fun <$ (reservedOp name1)) <$ reservedOp name0)
binaryL :: Monad m => String -> (a -> a -> a) -> Operator (ParsecT String u m) a
binaryL  name fun = InfixL (do{ reservedOp name; pure fun })
-- binaryN  name fun = InfixN (do{ reservedOp name; pure fun })
-- binaryR  name fun = InfixR (do{ reservedOp name; pure fun })

-- opExpr is a JavaExpr with operators
opExpr :: Monad m => ParsecT String u m JavaExpr
opExpr    = makeExprParser javaExpr table

varDecl :: Monad m => ParsecT String u m JavaStmt
varDecl = (do
    x <- reserved "var" *> identifier
    expr <- symbol "=" *> opExpr <* symbol ";"
    pure $ VarDecl x expr) <?> "varDecl"

assign :: Monad m => ParsecT String u m JavaStmt
assign = (do
    x <- identifier
    expr <- symbol "=" *> opExpr <* symbol ";"
    pure $ Assign x expr) <?> "assign"

setItem :: Monad m => ParsecT String u m JavaStmt
setItem = (do
    x <- identifier
    reserved ".setItem"
    (y, z) <- parens ((,) <$> javaExpr <*> (symbol "," *> opExpr))
    reserved ";"
    pure $ SetItem x y z) <?> "setItem"

continue :: Monad m => ParsecT String u m JavaStmt
continue = (do
    reserved "continue"
    reserved ";"
    pure Continue) <?> "continue"

break' :: Monad m => ParsecT String u m JavaStmt
break' = (do
    reserved "break"
    reserved ";"
    pure Break) <?> "break"

exprAsStmt :: Monad m => ParsecT String u m JavaStmt
exprAsStmt = (do
    e <- javaExpr
    reserved ";"
    pure $ ExprAsStmt e) <?> "exprAsStmt"

rangeFor :: Monad m => ParsecT String u m JavaStmt
rangeFor = (do
    reserved "var"
    _ <- identifier
    reserved "= e.invokeMethod(e.invokeFunction(\"range\","
    rangeArgExprs <- sepBy1 javaExpr (symbol ",") <* reserved "), \"__iter__\");"
    reserved "while (true)"
    (loopVar'', body') <- braces $ do
        reserved "try"
        (loopVar', body) <- braces $ do
            reserved "var"
            loopVar <- javaExpr
            reserved "= (PyObject) e.invokeMethod"
            parens $ do
                _ <- identifier -- also loopVar
                reserved ", \"__next__\""
            _ <- symbol ";"
            body <- javaStmts
            pure (loopVar, body)
        reserved "catch (Exception ex)"
        braces $ reserved "break;"
        pure (loopVar', body)
    let rangeArgs = case
            rangeArgExprs of
                [x] -> emptyRangeArgs { stop = x }
                [x, y] -> emptyRangeArgs { start = Just x, stop = y }
                [x, y, z] -> emptyRangeArgs { start = Just x, stop = y, step = Just z }
                _ -> error ("can't handle range args: " <> show rangeArgExprs)
    pure $ RangeFor loopVar'' rangeArgs body') <?> "rangeFor"

while :: Monad m => ParsecT String u m JavaStmt
while = (do
    reserved "while"
    cond <- parens javaExpr
    body <- braces javaStmts
    pure $ While cond body) <?> "while"

if' :: Monad m => ParsecT String u m JavaStmt
if' = (do
    reserved "if"
    cond <- parens javaExpr
    body <- braces javaStmts
    pure $ If cond body) <?> "if"

javaExpr :: Monad m => ParsecT String u m JavaExpr
javaExpr = do
    _ <- optional (reserved "(PyObject)")
    parens opExpr
        <|> num
        <|> none
        <|> pyObjectSuffix
        <|> try funCall
        <|> try isTrue
        <|> try isFalse
        <|> try equals
        <|> try true'
        <|> try false'
        <|> try getAttribute
        <|> tuple
        <|> varOrGetItem

pyObjectSuffix :: Monad m => ParsecT String u m JavaExpr
pyObjectSuffix = parens pyObjectSuffix <|> funRef <|> invokeMethod <|> invokeFunction where

    funRef :: Monad m => ParsecT String u m JavaExpr
    funRef = (do
        reserved "e.eval"
        x <- parens stringLiteral
        pure $ FunRef x) <?> "funRef"

    invokeMethod :: Monad m => ParsecT String u m JavaExpr
    invokeMethod = (do
        reserved "e.invokeMethod"
        parens $ do
            rcv <- javaExpr
            _ <- symbol ","
            method <- stringLiteral
            args <- optionMaybe (symbol "," *> sepBy javaExpr (symbol ","))
            pure $ InvokeMethod rcv method (fromMaybe [] args)) <?> "invokeMethod"

    invokeFunction :: Monad m => ParsecT String u m JavaExpr
    invokeFunction = (do
        reserved "e.invokeFunction"
        parens $ do
            function <- stringLiteral
            args <- optionMaybe (symbol "," *> sepBy javaExpr (symbol ","))
            pure $ InvokeFunction function (fromMaybe [] args)) <?> "invokeFunction"

javaStmt :: Monad m => ParsecT String u m JavaStmt
javaStmt =
    try rangeFor
    <|> varDecl
    <|> try assign
    <|> while
    <|> if'
    <|> try setItem
    <|> continue
    <|> break'
    <|> exprAsStmt

javaStmts :: Monad m => ParsecT String u m [JavaStmt]
javaStmts = many javaStmt

rangeArgsToPython :: RangeArgs -> String
rangeArgsToPython (MkRangeArgs{..}) = intercalate ", " . map exprToPython . catMaybes $ [start, Just stop, step]

stmtToPython :: JavaStmt -> String
stmtToPython (VarDecl _ (FunRef _)) = "" -- these are boilerplate that's only necessary for Detroit
stmtToPython (VarDecl n expr) = exprToPython (Var n) <> " = " <> exprToPython expr
stmtToPython (Assign n expr) = exprToPython (Var n) <> " = " <> exprToPython expr
stmtToPython (SetItem n x y) = exprToPython (Var n) <> "[" <> exprToPython x <> "] = " <> exprToPython y
stmtToPython (RangeFor loopVar rangeArgs stmts) = "for " <> exprToPython loopVar <> " in range(" <> rangeArgsToPython rangeArgs <> "):\n" <> case stmts of
        [] -> "\tpass"
        (_:_) -> dropEnd 1 . unlines . map (dropEnd 1 . unlines . map ("\t"<>) . lines . stmtToPython) $ stmts
stmtToPython (While cond body) = "while " <> exprToPython cond <> ":\n" <> (dropEnd 1 . unlines . map (dropEnd 1 . unlines . map ("\t" <>) . lines . stmtToPython) $ body)
stmtToPython (If cond body) = "if " <> exprToPython cond <> ":\n" <> (dropEnd 1 . unlines . map (dropEnd 1 . unlines . map ("\t" <>) . lines . stmtToPython) $ body)
stmtToPython Continue = "continue"
stmtToPython Break = "break"
stmtToPython (ExprAsStmt e) = exprToPython e

stmtsToPython :: [JavaStmt] -> String
stmtsToPython = unlines . map stmtToPython

camelToSnake :: String -> String
camelToSnake "" = ""
camelToSnake (x : xs) | isUpper x = '_' : toLower x : camelToSnake xs
camelToSnake (x : xs) = x : camelToSnake xs

exprToPython :: JavaExpr -> String
exprToPython (Num x) = show x
exprToPython None = "None"
exprToPython (FunRef n) = n
exprToPython (FunCall n xs) = nameToPython n <> "(" <> exprsToPython xs <> ")" where
    nameToPython :: String -> String
    nameToPython ('D':'o':'t':c:rest) = '.' : toLower c : nameToPython rest
    nameToPython (c:rest)             = c : nameToPython rest
    nameToPython []                   = []
exprToPython (Tuple xs) = "(" <> exprsToPython xs <> ", )"
exprToPython (Var "intDType") = "int"
exprToPython (Var n) = camelToSnake n
exprToPython (GetItem n x) = exprToPython (Var n) <> "[" <> exprToPython x <> "]"
exprToPython (InvokeMethod x n xs) = case n of
    "__add__" -> "(" <> exprToPython x <> " + " <> exprsToPython xs <> ")"
    "__mod__" -> "(" <> exprToPython x <> " % " <> exprsToPython xs <> ")"
    "__sub__" -> "(" <> exprToPython x <> " - " <> exprsToPython xs <> ")"
    "__mul__" -> "(" <> exprToPython x <> " * " <> exprsToPython xs <> ")"
    "__eq__" -> "(" <> exprToPython x <> " == " <> exprsToPython xs <> ")"
    "__ne__" -> "(" <> exprToPython x <> " != " <> exprsToPython xs <> ")"
    "__le__" -> "(" <> exprToPython x <> " <= " <> exprsToPython xs <> ")"
    "__lt__" -> "(" <> exprToPython x <> " < " <> exprsToPython xs <> ")"
    "__gt__" -> "(" <> exprToPython x <> " > " <> exprsToPython xs <> ")"
    _ -> exprToPython x <> "." <> n <> "(" <> exprsToPython xs <> ")"
exprToPython (InvokeFunction n xs) = n <> "(" <> exprsToPython xs <> ")"
exprToPython (IsTrue x) = exprToPython (Var x)
exprToPython (IsFalse x) = "not " <> exprToPython (Var x)
exprToPython (Equals x y) = exprToPython x <> ".__eq__(" <> exprToPython y <> ")"
exprToPython (Ternary x y z) = exprToPython y <> " if " <> exprToPython x <> " else " <> exprToPython z
exprToPython (And x y) = exprToPython x <> " and " <> exprToPython y
exprToPython True' = "True"
exprToPython False' = "False"
exprToPython (GetAttribute n attr) = n <> "." <> attr

exprsToPython :: [JavaExpr] -> String
exprsToPython = intercalate ", " . map exprToPython

parseExpr :: String -> Either ParseError JavaExpr
parseExpr str = parse (javaExpr <* eof) "" str

parseOpExpr :: String -> Either ParseError JavaExpr
parseOpExpr str = parse (opExpr <* eof) "" str

parseStmt :: String -> Either ParseError JavaStmt
parseStmt str = parse (javaStmt <* eof) "" str

parseStmts :: String -> Either ParseError [JavaStmt]
parseStmts str = parse (many javaStmt <* eof) "" str

main :: IO ()
main = do

--     str <- getContents
--     let res = parse (rangeFor <* eof) "" str
--     case res of
--         Left err -> error $ show err
--         Right res' -> putStrLn . stmtToPython $ res'
--     -- parseTestLog False (opExpr <* eof) "cond ? x : y"

    str <- getContents
    let res = parse (javaStmts <* eof) "" str
    case res of
        Left err -> error $ show err
        Right res' -> putStrLn . stmtsToPython $ res'
    -- parseTestLog False (opExpr <* eof) "cond ? x : y"

--     hspec $ do
--         describe "expression parser" $ do
--             it "parses num" $ do
--                 parseExpr "e.fromJava(42)" `shouldBe` Right (Num 42)
--             it "parses funRef" $ do
--                 parseExpr "(PyObject) e.eval(\"np.empty\")" `shouldBe` Right (FunRef "np.empty")
--             it "parses funCall" $ do
--                 parseExpr "npDotEmpty.call(e.fromJava(42))" `shouldBe` Right (FunCall "npDotEmpty" [Num 42])
--             it "parses tuple" $ do
--                 parseExpr "e.newPyTuple(e.fromJava(1), e.fromJava(2))" `shouldBe` Right (Tuple [Num 1, Num 2])
--             it "parses var" $ do
--                 parseExpr "foo" `shouldBe` Right (Var "foo")
--             it "parses getItem" $ do
--                 parseExpr "volume.getItem(x)" `shouldBe` Right (GetItem "volume" (Var "x"))
--             it "parses invokeMethod with params" $ do
--                 parseExpr "(PyObject) e.invokeMethod(x, \"foo\", y, z)" `shouldBe` Right (InvokeMethod (Var "x") "foo" [Var "y", Var "z"])
--             it "parses invokeMethod without params" $ do
--                 parseExpr "(PyObject) e.invokeMethod(x, \"foo\")" `shouldBe` Right (InvokeMethod (Var "x") "foo" [])
--             it "parses invokeFunction" $ do
--                 parseExpr "e.invokeFunction(\"range\", dims.getItem(v))" `shouldBe` Right (InvokeFunction "range" [GetItem "dims" (Var "v")])
--             it "parses ternary" $ do
--                 parseOpExpr "cond.isTrue() ? x : y" `shouldBe` Right (Ternary (IsTrue "cond") (Var "x") (Var "y"))
--             it "parses isTrue" $ do
--                 parseExpr "x.isTrue()" `shouldBe` Right (IsTrue "x")
--             it "parses logical and" $ do
--                 parseOpExpr "x && y" `shouldBe` Right (And (Var "x") (Var "y"))
--             it "parses true" $ do
--                 parseExpr "e.getTrue()" `shouldBe` Right True'
--             it "parses false" $ do
--                 parseExpr "e.getFalse()" `shouldBe` Right False'
--             it "parses getAttribute" $ do
--                 parseExpr "a.getAttribute(\"shape\")" `shouldBe` Right (GetAttribute "a" "shape")

--         describe "statement parser" $ do
--             it "parses varDecl" $ do
--                 parseStmt "var x = e.fromJava(42);" `shouldBe` Right (VarDecl "x" (Num 42))
--             it "parses assign" $ do
--                 parseStmt "x = e.fromJava(42);" `shouldBe` Right (Assign "x" (Num 42))
--             it "parses setItem" $ do
--                 parseStmt "volume.setItem(x, e.fromJava(42));" `shouldBe` Right (SetItem "volume" (Var "x") (Num 42))
--             it "parses statements with comments" $ do
--                 parseStmts "var x = e.fromJava(42);\n// reassign x\nx = e.fromJava(43);" `shouldBe` Right [VarDecl "x" (Num 42), Assign "x" (Num 43)]
--             it "parses rangeFor" $ do
--                 parseStmt """
--                     var iIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(5)), "__iter__");
--                     while (true) {
--                         try {
--                             var i = (PyObject) e.invokeMethod(iIt, "__next__");
--                             var shouldSkip = skip.getItem(idxTuple);
--                         } catch (Exception ex) {
--                             break;
--                         }
--                     }
--                     """ `shouldBe` Right (RangeFor (Var "i") (emptyRangeArgs { stop = (Num 5) }) [VarDecl "shouldSkip" (GetItem "skip" (Var "idxTuple"))])
--             it "parses stmt with parenthesized expr" $ do
--                 parseStmt "var sOp = (PyObject) (s == e.fromJava(0) ? e.invokeMethod(dims.getItem(d), \"__sub__\", e.fromJava(1)) : e.fromJava(0));"
--                     `shouldBe` Right (VarDecl "sOp" (Ternary (Equals (Var "s") (Num 0)) (InvokeMethod (GetItem "dims" (Var "d")) "__sub__" [Num 1]) (Num 0)))
--             it "parses while" $ do
--                 parseStmt "while (volume.isTrue()) { var x = e.fromJava(42); }" `shouldBe` Right (While (IsTrue "volume") [VarDecl "x" (Num 42)])
--             it "parses continue" $ do
--                 parseStmt "continue;" `shouldBe` Right Continue
--             it "parses break" $ do
--                 parseStmt "break;" `shouldBe` Right Break

--         describe "Python conversion" $ do
--             it "converts num" $ do
--                 exprToPython (Num 42) `shouldBe` "42"
--             it "converts none" $ do
--                 exprToPython (None) `shouldBe` "None"
--             it "converts funRef" $ do
--                 exprToPython (FunRef "np.empty") `shouldBe` "np.empty"
--             it "converts funCall" $ do
--                 exprToPython (FunCall "npDotEmpty" [Num 3]) `shouldBe` "np.empty(3)"
--             it "converts tuple" $ do
--                 exprToPython (Tuple [Num 1, Num 2]) `shouldBe` "(1, 2, )"
--             it "converts var" $ do
--                 exprToPython (Var "foo") `shouldBe` "foo"
--             it "converts var with camel case to snake case" $ do
--                 exprToPython (Var "fooBarBaz") `shouldBe` "foo_bar_baz"
--             it "converts getItem" $ do
--                 exprToPython (GetItem "foo" (Var "x")) `shouldBe` "foo[x]"
--             it "converts getItem with camel case" $ do
--                 exprToPython (GetItem "fooBar" (Var "x")) `shouldBe` "foo_bar[x]"
--             it "converts invokeMethod" $ do
--                 exprToPython (InvokeMethod (Var "a") "fill" [Num 3]) `shouldBe` "a.fill(3)"
--             it "converts invokeFunction" $ do
--                 exprToPython (InvokeFunction "range" [Num 5]) `shouldBe` "range(5)"
--             it "converts isTrue" $ do
--                 exprToPython (IsTrue "x") `shouldBe` "x"
--             it "converts ternary" $ do
--                 exprToPython (Ternary (IsTrue "x") (Num 1) (Num 2)) `shouldBe` "1 if x else 2"
--             it "converts ternary with camel case" $ do
--                 exprToPython (Ternary (IsTrue "fooBar") (Num 1) (Num 2)) `shouldBe` "1 if foo_bar else 2"
--             it "converts inverted ternary" $ do
--                 exprToPython (Ternary (IsFalse "x") (Num 1) (Num 2)) `shouldBe` "1 if not x else 2"
--             it "converts equals" $ do
--                 exprToPython (Equals (Var "x") (Var "y")) `shouldBe` "x.__eq__(y)"
--             it "converts logical and" $ do
--                 exprToPython (And (Var "x") (Var "y")) `shouldBe` "x and y"
--             it "converts true" $ do
--                 exprToPython True' `shouldBe` "True"
--             it "converts false" $ do
--                 exprToPython False' `shouldBe` "False"
--             it "converts getAttribute" $ do
--                 exprToPython (GetAttribute "n" "attr") `shouldBe` "n.attr"
--             it "converts intDType" $ do
--                 exprToPython (Var "intDType") `shouldBe` "int"

--             it "converts varDecl" $ do
--                 stmtToPython (VarDecl "x" (Num 42)) `shouldBe` "x = 42"
--             it "converts assign" $ do
--                 stmtToPython (Assign "x" (Num 42)) `shouldBe` "x = 42"
--             it "converts assign with camel case" $ do
--                 stmtToPython (Assign "xFoo" (Num 42)) `shouldBe` "x_foo = 42"
--             it "converts setItem" $ do
--                 stmtToPython (SetItem "volume" (Var "x") (Num 42)) `shouldBe` "volume[x] = 42"
--             it "converts setItem with camel case" $ do
--                 stmtToPython (SetItem "volumeList" (Var "x") (Num 42)) `shouldBe` "volume_list[x] = 42"
--             it "converts rangeFor without body" $ do
--                 stmtToPython (RangeFor (Var "n") (emptyRangeArgs { stop = (Num 5) }) []) `shouldBe` "for n in range(5):\n\tpass"
--             it "converts rangeFor with two args" $ do
--                 stmtToPython (RangeFor (Var "n") (emptyRangeArgs { start = Just (Num 3), stop = Num 5 }) []) `shouldBe` "for n in range(3, 5):\n\tpass"
--             it "converts rangeFor with three args" $ do
--                 stmtToPython (RangeFor (Var "n") (emptyRangeArgs { start = Just (Num 3), stop = Num 5, step = Just (Num 2) }) []) `shouldBe` "for n in range(3, 5, 2):\n\tpass"
--             it "converts rangeFor with body" $ do
--                 stmtToPython (RangeFor (Var "n") (emptyRangeArgs { stop = (Num 5) }) [VarDecl "x" (Num 42)]) `shouldBe` "for n in range(5):\n\tx = 42"
--             it "converts continue" $ do
--                 stmtToPython Continue `shouldBe` "continue"
--             it "converts break" $ do
--                 stmtToPython Break `shouldBe` "break"
--             it "converts while" $ do
--                 stmtToPython (While (IsTrue "p") [VarDecl "x" (Num 42)]) `shouldBe` "while p:\n\tx = 42"
--             it "converts if" $ do
--                 stmtToPython (If (IsTrue "p") [VarDecl "x" (Num 42)]) `shouldBe` "if p:\n\tx = 42"
--             it "converts stmts" $ do
--                 stmtsToPython [VarDecl "x" (Num 42), VarDecl "y" (Num 43)] `shouldBe` "x = 42\ny = 43\n"

-- --     -- putStrLn str
-- --     -- parseTestLog False (javaStmts <* eof) str
-- --     -- let out = do
-- --     --         stmts <- parse (javaStmts <* eof) "" str
-- --     --         pure $ stmtsToPython stmts
-- --     -- case out of
-- --     --     Left err -> error (show err)
-- --     --     Right res -> putStrLn res
-- --     -- parseTestLog False (javaExpr <* eof) str
-- --     -- parseTest (javaExpr <* eof) str
-- --     -- parseTest (javaStmt <* eof) str
-- --     -- parseTestLog False (javaStmt <* eof) str

