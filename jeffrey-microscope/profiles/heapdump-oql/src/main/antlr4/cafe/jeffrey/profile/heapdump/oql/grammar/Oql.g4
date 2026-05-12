// Jeffrey OQL grammar — MAT-flavoured surface, SQL-style operators only.
// Generates Java parser + lexer + visitor in
// cafe.jeffrey.profile.heapdump.oql.grammar (package derived from the source
// path under src/main/antlr4) via the antlr4-maven-plugin.
grammar Oql;

// ---------- Parser rules ----------

statement
    : queryAtom (UNION queryAtom)* EOF
    ;

queryAtom
    : '(' query ')'
    | query
    ;

query
    : selectClause fromClause whereClause? groupByClause? havingClause? orderByClause? limitClause?
    ;

selectClause
    : SELECT selectModifier selectList
    | SELECT selectList selectModifier
    | SELECT selectList
    ;

selectModifier
    : DISTINCT
    | AS RETAINED SET
    ;

selectList
    : STAR
    | selectItem (',' selectItem)*
    ;

selectItem
    : OBJECTS? expression (AS alias=identifier)?
    ;

fromClause
    : FROM OBJECTS? fromKind? fromSource alias=identifier?
    ;

fromKind
    : INSTANCEOF
    | IMPLEMENTS
    ;

fromSource
    : qualifiedHeapHelper        # FromHeapHelper
    | functionCall               # FromFunctionCall
    | className                  # FromClassName
    | STRING_LITERAL             # FromRegex
    | '(' query ')'              # FromSubquery
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY expression (',' expression)*
    ;

havingClause
    : HAVING expression
    ;

orderByClause
    : ORDER BY orderItem (',' orderItem)*
    ;

orderItem
    : expression (ASC | DESC)?
    ;

limitClause
    : LIMIT n=INTEGER_LITERAL (OFFSET m=INTEGER_LITERAL)?
    ;

// Expressions — precedence climbing, lowest to highest.

expression
    : orExpr
    ;

orExpr
    : andExpr (OR andExpr)*
    ;

andExpr
    : notExpr (AND notExpr)*
    ;

notExpr
    : NOT notExpr                                # NotExpression
    | comparisonExpr                             # ComparisonPassthrough
    ;

comparisonExpr
    : additiveExpr ( ('=' | '!=' | '<>' | '<' | '<=' | '>' | '>=') additiveExpr )?    # BinaryCompare
    | additiveExpr LIKE additiveExpr                                                  # LikeCompare
    | additiveExpr NOT? IN '(' expression (',' expression)* ')'                       # InCompare
    | additiveExpr IS NULL                                                            # IsNullCompare
    | additiveExpr IS NOT NULL                                                        # IsNotNullCompare
    ;

additiveExpr
    : multiplicativeExpr ( ('+' | '-') multiplicativeExpr )*
    ;

multiplicativeExpr
    : unaryExpr ( ('*' | '/') unaryExpr )*
    ;

unaryExpr
    : '-' unaryExpr                                                                   # Negation
    | postfixExpr                                                                     # PostfixPassthrough
    ;

// path access and array indexing are postfix operations chained off a primary
postfixExpr
    : primaryExpr ( pathSegment | indexSegment )*
    ;

pathSegment
    : '.' '@' identifier                        # AttrPathSegment
    | '.' identifier                            # FieldPathSegment
    ;

indexSegment
    : '[' expression ']'
    ;

primaryExpr
    : literal                                                                         # LiteralPrimary
    | caseExpression                                                                  # CasePrimary
    | functionCall                                                                    # FunctionCallPrimary
    | attrRef                                                                         # AttrRefPrimary
    | qualifiedHeapHelper                                                             # HeapHelperPrimary
    | identifier                                                                      # BindingRefPrimary
    | '(' expression ')'                                                              # ParenExpression
    | '(' query ')'                                                                   # SubqueryPrimary
    ;

caseExpression
    : CASE whenClause+ (ELSE expression)? END
    ;

whenClause
    : WHEN expression THEN expression
    ;

functionCall
    : identifier '(' (STAR | argList)? ')'
    ;

qualifiedHeapHelper
    : HEAP '.' identifier '(' argList? ')'
    ;

argList
    : expression (',' expression)*
    ;

attrRef
    : '@' identifier
    ;

literal
    : INTEGER_LITERAL                                                                 # IntLiteral
    | HEX_LITERAL                                                                     # HexLiteral
    | DECIMAL_LITERAL                                                                 # DecimalLiteral
    | STRING_LITERAL                                                                  # StringLiteral
    | TRUE                                                                            # TrueLiteral
    | FALSE                                                                           # FalseLiteral
    | NULL                                                                            # NullLiteral
    ;

className
    : qualifiedName arrayDims?
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

arrayDims
    : ('[' ']')+
    ;

identifier
    : IDENTIFIER
    | nonReservedKeyword
    ;

// keywords like OBJECTS, ASC, DESC etc. are allowed as plain identifiers
nonReservedKeyword
    : OBJECTS | ASC | DESC | RETAINED | SET | HEAP
    ;

// ---------- Lexer rules ----------

// Keywords (case-insensitive; see lexer fragments below)
SELECT      : S E L E C T ;
FROM        : F R O M ;
WHERE       : W H E R E ;
GROUP       : G R O U P ;
BY          : B Y ;
HAVING      : H A V I N G ;
ORDER       : O R D E R ;
ASC         : A S C ;
DESC        : D E S C ;
LIMIT       : L I M I T ;
OFFSET      : O F F S E T ;
UNION       : U N I O N ;
DISTINCT    : D I S T I N C T ;
AS          : A S ;
RETAINED    : R E T A I N E D ;
SET         : S E T ;
OBJECTS     : O B J E C T S ;
INSTANCEOF  : I N S T A N C E O F ;
IMPLEMENTS  : I M P L E M E N T S ;
AND         : A N D ;
OR          : O R ;
NOT         : N O T ;
LIKE        : L I K E ;
IN          : I N ;
IS          : I S ;
NULL        : N U L L ;
TRUE        : T R U E ;
FALSE       : F A L S E ;
CASE        : C A S E ;
WHEN        : W H E N ;
THEN        : T H E N ;
ELSE        : E L S E ;
END         : E N D ;
HEAP        : H E A P ;

STAR        : '*' ;

HEX_LITERAL     : '0' [xX] [0-9a-fA-F]+ ;
DECIMAL_LITERAL : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? ;
INTEGER_LITERAL : [0-9]+ ;

// String literal: double- or single-quoted with backslash escapes.
STRING_LITERAL
    : '"'  ( '\\' . | ~["\\] )* '"'
    | '\'' ( '\\' . | ~['\\] )* '\''
    ;

IDENTIFIER
    : [a-zA-Z_$] [a-zA-Z_$0-9]*
    ;

// Whitespace and comments
WS              : [ \t\r\n]+    -> skip ;
LINE_COMMENT    : '--' ~[\r\n]* -> skip ;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip ;

// Case-insensitive letter fragments
fragment A : [aA] ; fragment B : [bB] ; fragment C : [cC] ; fragment D : [dD] ;
fragment E : [eE] ; fragment F : [fF] ; fragment G : [gG] ; fragment H : [hH] ;
fragment I : [iI] ; fragment J : [jJ] ; fragment K : [kK] ; fragment L : [lL] ;
fragment M : [mM] ; fragment N : [nN] ; fragment O : [oO] ; fragment P : [pP] ;
fragment Q : [qQ] ; fragment R : [rR] ; fragment S : [sS] ; fragment T : [tT] ;
fragment U : [uU] ; fragment V : [vV] ; fragment W : [wW] ; fragment X : [xX] ;
fragment Y : [yY] ; fragment Z : [zZ] ;
