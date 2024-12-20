package obsidian.lang.java.obsidian

import obsidian.lang.java.scalangj.Syntax.*

object CPSFixture {
        // fixtures
    // id cps 
    val id_cps = MemberDecl_(
        FieldDecl(List(Public, Static),
            RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
            List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
                 ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))),
            List(VarDecl(VarId(Ident("id")),Some(InitExp(Lambda(LambdaSingleParam(Ident("e")),
            LambdaBlock(Block(List(BlockStmt_(Return(Some(Lit(NullLit))))))))))))))
    // id_handler TODO: check
    val id_handler_cps = MemberDecl_(
        FieldDecl(List(Public, Static),
            RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
            List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
                 ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))),
            List(VarDecl(VarId(Ident("idHandler")),Some(InitExp(Lambda(LambdaSingleParam(Ident("e")),
            LambdaBlock(Block(List(BlockStmt_(Return(Some(Lit(NullLit))))))))))))))
    // loop
    val loop_cps = MemberDecl_(
        MethodDecl(List(Public, Static),List(),
        Some(RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function")
        ,List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function")
        ,List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List())))))))))))))))))),
        Ident("loop"),
        List(FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Boolean"),List()))))))))))),
        false,
        VarId(Ident("cond"))), FormalParam(List(),
        RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("visitor"))), FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("exit")))),
        List(),None,
        MethodBody(Some(Block(List(BlockStmt_(Return(Some(Lambda(LambdaSingleParam(Ident("raise")),
        LambdaExpression_(Lambda(LambdaSingleParam(Ident("k")),
        LambdaBlock(Block(List(BlockStmt_(IfThenElse(MethodInv(MethodCall(Name(List(Ident("cond"), Ident("apply"))),List(Lit(NullLit)))),
        StmtBlock(Block(List(BlockStmt_(Return(Some(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("visitor"), Ident("apply"))),
        List(ExpName(Name(List(Ident("raise"))))))),List(),Ident("apply"),List(Lambda(LambdaSingleParam(Ident("n")),
        LambdaExpression_(MethodInv(PrimaryMethodCall(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("loop"))),
        List(ExpName(Name(List(Ident("cond")))), ExpName(Name(List(Ident("visitor")))), ExpName(Name(List(Ident("exit"))))))),List(),
        Ident("apply"),List(ExpName(Name(List(Ident("raise"))))))),List(),Ident("apply"),List(ExpName(Name(List(Ident("k")))))))))))))))))),
        StmtBlock(Block(List(BlockStmt_(Return(Some(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("exit"), Ident("apply"))),
        List(ExpName(Name(List(Ident("raise"))))))),List(),Ident("apply"),List(ExpName(Name(List(Ident("k"))))))))))))))))))))))))))))))

    // seq_cps 
    val seq_cps = MemberDecl_(
        MethodDecl(List(Public, Static),List(),
        Some(RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List())))))))))))))))))),
        Ident("seq"),
        List(FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("first"))), FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("second")))),List(),None,MethodBody(Some(Block(List(BlockStmt_(Return(Some(Lambda(LambdaSingleParam(Ident("raise")),
        LambdaExpression_(Lambda(LambdaSingleParam(Ident("k")),
        LambdaExpression_(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("first"), Ident("apply"))),List(ExpName(Name(List(Ident("raise"))))))),
        List(),Ident("apply"),List(Lambda(LambdaSingleParam(Ident("n")),
        LambdaExpression_(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("second"), Ident("apply"))),List(ExpName(Name(List(Ident("raise"))))))),
        List(),Ident("apply"),List(ExpName(Name(List(Ident("k")))))))))))))))))))))))))
        
    // ifelse_cps 
    val ifelse_cps = MemberDecl_(
        MethodDecl(List(Public, Static),List(),Some(RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List())))))))))))))))))),
        Ident("ifelse"),
        List(FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Boolean"),List()))))))))))),
        false,
        VarId(Ident("cond"))), 
        FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("th"))), 
        FormalParam(List(),RefType_(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Exception"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("java"),List()), (Ident("util"),List()), (Ident("function"),List()), (Ident("Function"),
        List(ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))), 
        ActualType(ClassRefType(ClassType(List((Ident("Void"),List()))))))))))))))))),
        false,
        VarId(Ident("el")))),
        List(),None,
        MethodBody(Some(Block(List(BlockStmt_(Return(Some(Lambda(LambdaSingleParam(Ident("raise")),
        LambdaExpression_(Lambda(LambdaSingleParam(Ident("k")),LambdaBlock(Block(List(BlockStmt_(IfThenElse(MethodInv(MethodCall(Name(List(Ident("cond"), 
        Ident("apply"))),List(Lit(NullLit)))),StmtBlock(Block(List(BlockStmt_(Return(Some(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("th"), Ident("apply"))),
        List(ExpName(Name(List(Ident("raise"))))))),
        List(),
        Ident("apply"),List(ExpName(Name(List(Ident("k"))))))))))))),
        StmtBlock(Block(List(BlockStmt_(Return(Some(MethodInv(PrimaryMethodCall(MethodInv(MethodCall(Name(List(Ident("el"), Ident("apply"))),
        List(ExpName(Name(List(Ident("raise"))))))),List(),
        Ident("apply"),List(ExpName(Name(List(Ident("k"))))))))))))))))))))))))))))))
}
