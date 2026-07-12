import Control.Monad (guard)
import Control.Monad.Trans.Class (lift)
import Control.Monad.Trans.State
import Data.List (intercalate)
import System.IO
import Text.Parsec
import Text.Parsec.Expr
import Text.Parsec.Language (haskellDef)
import qualified Text.Parsec.Token as P

lexer = P.makeTokenParser haskellDef

brackets = P.brackets lexer
identifier = P.identifier lexer
integer = P.integer lexer
parens = P.parens lexer
reservedOp = P.reservedOp lexer

str :: String
-- str = "x[2] + dims[2] * (x[1] + dims[1] * x[0])"
str = "x[u] + dims[u] * x[v]"

indexedVar :: Parsec String () Expr
indexedVar = do
    x <- identifier
    idx <- brackets expr
    pure $ IndexedVar x idx

var = Var <$> identifier

num = Num . fromInteger <$> integer

-- TODO: stupid name, as a num is not a var
varExpr :: Parsec String () Expr
varExpr = parens expr <|> try indexedVar <|> var <|> num

data Expr =
    Var String
    | Num Int
    | IndexedVar String Expr
    | Add Expr Expr
    | Mul Expr Expr
    deriving (Eq, Ord, Show)

expr :: Parsec String () Expr
expr = buildExpressionParser table varExpr

table = [
    [ binary "*" Mul AssocLeft ]
    , [ binary "+" Add AssocLeft ]
    ]

binary  name fun assoc = Infix (do{ reservedOp name; return fun }) assoc
prefix  name fun       = Prefix (do{ reservedOp name; return fun })
postfix name fun       = Postfix (do{ reservedOp name; return fun })

class Pretty a where
    pretty :: a -> String

instance Pretty Expr where
    pretty (Var s) = s
    pretty (Num n) = show n
    pretty (IndexedVar s e) = s ++ "[" ++ pretty e ++ "]"
    -- TODO: I guess these need parentheses
    -- pretty (Add e0 e1) = pretty e0 ++ "+" ++ pretty e1
    -- pretty (Mul e0 e1) = pretty e0 ++ "*" ++ pretty e1
-- | Deconstructs the AST into a list of coordinate terms and dimension terms.
-- flattenExpr :: Expr -> Maybe ([String], [String])
-- flattenExpr e@(IndexedVar x idx) = Just ([pretty e], [])
-- flattenExpr (Add e1 e2) = do
--     (c1, d1) <- flattenExpr e1
--     (c2, d2) <- flattenExpr e2
--     pure (c1 ++ c2, d1 ++ d2)
-- flattenExpr (Mul e0@(IndexedVar d idx) e1) = do
--     (c, dRight) <- flattenExpr e1
--     pure (c, pretty e0 : dRight)
-- flattenExpr _ = Nothing

flattenExpr :: Expr -> StateT Int Maybe ([String], [String])
flattenExpr e@(IndexedVar x (Num idx)) = do
    expected <- get
    guard (idx == expected)       -- Enforce that cursor[idx] matches the monotonic sequence
    put (expected + 1)            -- Monotonically step to the next expected dimension
    pure ([pretty e], [])

flattenExpr (Add e1 e2) = do
    (c2, d2) <- flattenExpr e2
    (c1, d1) <- flattenExpr e1
    pure (c2 ++ c1, d2 ++ d1)

flattenExpr (Mul e0@(IndexedVar d (Num idx)) e1) = do
    (c, dRight) <- flattenExpr e1
    current <- get
    guard (idx == current)    -- Enforce that dims[idx] matches the current active stride level
    pure (c, pretty e0 : dRight)

flattenExpr _ = lift Nothing       -- Fallback failure for unhandled expression patterns

-- | Processes a single line of input according to the transformation rules.
processLine :: String -> IO ()
processLine line = case parse expr "" line of
    Right ast -> case evalStateT (flattenExpr ast) 0 of
        Just (coords, dims) -> do
            -- Output the comma-separated coordinate accessors to STDOUT
            putStrLn $ intercalate ", " coords
            -- Format the shape representation, appending the unknown dimension '?'
            let shape = intercalate ", " . (++ ["?"]) $ dims
            hPutStrLn stderr $ "assuming shape (" ++ shape ++ ")"
        Nothing -> fallback
    Left _ -> fallback
  where
    fallback = do
        putStrLn line
        hPutStrLn stderr "."

main :: IO ()
main = do
    input <- getContents
    mapM_ processLine (lines input)
