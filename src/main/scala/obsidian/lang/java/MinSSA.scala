package obsidian.lang.java


import cats.kernel.Semilattice
import cats._
import cats.implicits._
import cats.data.StateT
import com.github.luzhuomi.scalangj.Syntax._
import obsidian.lang.java.ASTPath._
import scala.collection.immutable
import obsidian.lang.java.ASTUtils._


object MinSSAL {
  case class SSAMethodDecl(
      modifiers: List[Modifier],
      type_params: List[TypeParam],
      ty: Option[Type],
      id: Ident,
      formal_params: List[FormalParam],
      ex_types: List[ExceptionType],
      exp: Option[Exp],
      body: SSAMethodBody
  )

  case class SSAMethodBody(
      blocks: List[SSABlock]
  )

  case class SSABlock(
      label: Label,
      stmts: List[SSAStmt]
  )

  type Label = TCtx 

    sealed trait SSAStmt 
  // to handle nested decl, not in the paper
  case class SSAVarDecls(mods:List[Modifier], ty:Type, varDecls:List[VarDecl]) extends SSAStmt

  case class SSAAssert(exp:Exp, msg:Option[Exp]) extends SSAStmt

  case class SSAAssignments(stmts: List[Stmt]) extends SSAStmt

  case class SSAExps(stmts: List[Stmt]) extends SSAStmt
  
  case class SSAReturn(oexp:Option[Exp]) extends SSAStmt

  case class SSAThrow(exp:Exp) extends SSAStmt

  case class SSABreak(tlbl:Label) extends SSAStmt

  case class SSAContinue(tlbl:Label) extends SSAStmt

  // it defers from the paper here, we don't keep a method invocation as a seperate case
  //      it is combined with the assignments and exps cases, as there might be multiple
  //      function calls within a single statement. 
  //      we keep track of the function call and its context in the state
  // case class SSAMethodInvocation(methodInv:MethodInvocation) extends SSAStmt

  case object SSAEmpty extends SSAStmt

  /**
    * 
    *
    * @param tryStmts: try blocks
    * @param phiCatch: Phis before the catch block
    * @param params: parameters for the catch block
    * @param catchStmts: catch blocks
    * @param phiFinally: Phis after the catch block
    */
  case class SSATry(
    tryStmts:List[SSABlock], 
    phiCatch: List[Phi],
    param: FormalParam,
    catchStmts:List[SSABlock],
    phiFinally: List[Phi] 
  ) extends SSAStmt

  
  /**
    * 
    *
    * @param phiEntr: Phis at the entry of the while stmt
    * @param exp: boolean expression
    * @param stmts
    * @param phiExit: Phis at the exit of the while stmt
    */
  case class SSAWhile(
    phiEntr: List[Phi],
    exp: Exp,
    stmts:List[SSABlock],
    phiExit: List[Phi]
  ) extends SSAStmt


  /**
    * 
    * @param exp boolean expression
    * @param thenStmts then blocks
    * @param elseStmts else blocks
    * @param phiExit: Phis at the exit of the while stmt
    */
  case class SSAIf(
    exp:Exp,
    thenStmts:List[SSABlock],
    elseStmts:List[SSABlock],
    phiExit: List[Phi]
  ) extends SSAStmt


  // type Label = ASTPath 

  /**
    * A phi assignment
    *
    * @param srcVar 
    * @param renVar
    * @param rhs
    */
  case class Phi(
    srcVar:Name,
    renVar:Name, 
    rhs:Map[Label, Name]
  )


  /**
    * Source language Context
    * ctx ::= Box | ctx; | ctx; \overline{s} | s; ctx | if e {ctx} else {\overline{s}} | 
    *    if e {\overline{s}} else {ctx}  |  while e {ctx} | try {ctx} catch (T x) (\overline{s}) | 
    *     try {\overline{s}} catch (T x) {ctx}  
    */
  sealed trait SCtx 
  
  case object SBox extends SCtx

  case class SLast(ctx: SCtx) extends SCtx 

  case class SHead(ctx: SCtx) extends SCtx

  case class STail(ctx: SCtx) extends SCtx
  
  case class SThen(ctx: SCtx) extends SCtx

  case class SElse(ctx: SCtx) extends SCtx

  case class SWhile(ctx: SCtx) extends SCtx
  
  case class STry(ctx: SCtx) extends SCtx
  
  case class SCatch(ctx: SCtx) extends SCtx


  def putSCtx(outter:SCtx, inner:SCtx): SCtx = outter match {
    case SBox => inner 
    case SLast(o) => SLast(putSCtx(o, inner))
    case SHead(o) => SHead(putSCtx(o, inner))
    case STail(o) => STail(putSCtx(o, inner))
    case SThen(o) => SThen(putSCtx(o, inner))
    case SElse(o) => SElse(putSCtx(o, inner))
    case SWhile(o) => SWhile(putSCtx(o, inner))
    case STry(o) => STry(putSCtx(o, inner))
    case SCatch(o) => SCatch(putSCtx(o, inner))
  }

  /**
    * Target language context (SSA)
    * CTX ::= Box | CTX; | CTX; \overline{B} | B; CTX | if E {CTX} else {\overline{B}} | 
    *     if E {\overline{B}} else {CTX} join {\overline{\phi}}  | 
    *     if E {CTX} else {\overline{B}} join {\overline{\phi}}  |
    *     if E {\overline{B}} else {\overline{B}} join {BBox}    | 
    *     join {BBox} while E { \overline{B}} join {\overline{\phi}} |  
    *     join {\overline{\phi}} while E { CTX } join {\overline{\phi}} |
    *     join {\overline{\phi}} while E { \overline{B}} join {BBox} | 
    *     try {Ctx} join {\overline{\phi}} catch (T x) {\overline{B}} join {\overline{\phi}} |
    *     try {\overline{B}} join {BBox} catch (T x) {\overline{B}} join {\overline{\phi}} |
    *     try {\overline{B}} join {\overline{\phi}} catch (T x) {CTX} join {\overline{\phi}} | 
    *     try {\overline{B}} join {\overline{\phi}} catch (T x) {\overline{B}} join {BBox}}
    */
  sealed trait TCtx 

  case object TBox extends TCtx

  case class TLast(ctx:TCtx) extends TCtx

  case class THead(ctx:TCtx) extends TCtx

  case class TTail(ctx:TCtx) extends TCtx

  case class TThen(ctx:TCtx) extends TCtx

  case class TElse(ctx:TCtx) extends TCtx

  case object TIfPostPhi extends TCtx

  case class TWhilePrePhi(b:Int) extends TCtx

  case class TWhile(ctx:TCtx) extends TCtx

  case object TWhilePostPhi extends TCtx

  case class TTry(ctx:TCtx) extends TCtx

  case object TTryPeriPhi extends TCtx

  case class TCatch(ctx:TCtx) extends TCtx

  case object TTryPostPhi extends TCtx 

  

  def putTCtx(outter:TCtx, inner:TCtx): TCtx = outter match {
    case TBox => inner 
    case TLast(o) => TLast(putTCtx(o, inner))
    case THead(o) => THead(putTCtx(o, inner))
    case TTail(o) => TTail(putTCtx(o, inner))
    case TThen(o) => TThen(putTCtx(o, inner))
    case TElse(o) => TElse(putTCtx(o, inner))
    case TWhile(o) => TWhile(putTCtx(o, inner))
    case TTry(o) => TTry(putTCtx(o, inner))
    case TCatch(o) => TCatch(putTCtx(o, inner))
    case _ => outter
  }


  // variable mapping 
  // old and wrong
  // type VarMap = Map[Name, Map[SCtx, (TCtx, Name)]]
  type VarMap = Map[Name, Map[TCtx, (SCtx, Name)]]
  
  def unionVarMap(vm1:VarMap, vm2:VarMap):VarMap = vm2.toList.foldLeft(vm1)( (vm, kv) => kv match {
    case (name, m) => vm.get(name) match {
      case None => vm + (name -> m)
      case Some(m2) => vm + (name -> (m ++ m2))
    }
  })

  def diffVarMap(vm1:VarMap, vm2:VarMap):VarMap = listToVarMap(varMapToList(vm1).toSet.diff(varMapToList(vm2).toSet).toList)

  def listToVarMap(l:List[(Name, (TCtx, (SCtx, Name)))]):VarMap = l.foldLeft(Map():VarMap)( (vm, kv) => kv match {
    case (name, (tctx, (sctx, tname))) => vm.get(name) match {
      case None => vm + (name -> Map(tctx -> (sctx, tname)))
      case Some(m2) => m2.get(tctx) match {
        case None => vm + (name ->  (m2 + (tctx -> (sctx, tname))))
        case Some((sctx1, name1)) => vm // duplicate?
      }
    }
  })

  def varMapToList(vm:VarMap):List[(Name, (TCtx, (SCtx, Name)))] = vm.toList.flatMap( (n, m) => {
    m.toList.map( (tctx, sctx_n2) => (n, (tctx, sctx_n2))) 
  })


  /**
  * A state object for the conversion function
  *
  * @param varMap - the variable mapping
  * @param exitCtx - the exit context from the last block
  * @param eenv - the list of contexts that throw exception
  * @param benv - the list of contexts that contains a break statement 
  * @param cenv - the list of contexts that contains a continue statement
  * @param nestedDecls - the list of nested declared variables
  * @param methodInvs - the list of method invocations
  */
  case class State(
    varMap: VarMap, 
    exitCtx: TCtx,
    aenv: AEnv, // all non phi context so far
    eenv: EEnv, 
    benv: BEnv, // break context, breakee context
    cenv: CEnv, // continue context, continuee context
    nestedDecls: List[(TCtx, Ident, Type, List[Modifier])],
    methodInvs: List[(TCtx, MethodInvocation)],
    srcLabelEnv: Map[Ident, SCtx]
  )

  val initState:State = State(Map(), TBox, List(), List(), List(), List(), List(), List(), Map())

  def eenvFromState(st:State):EEnv = st match {
    case State(_, _, aenv, eenv, _, _, _, _,_ ) => eenv
  }

  def benvFromState(st:State):BEnv = st match {
    case State(_, _, aenv, _, benv, _, _, _, _) => benv
  }

  def cenvFromState(st:State):CEnv = st match {
    case State(_, _, aenv, _, _, cenv, _, _, _) => cenv
  }

  def eCtxFromState(st:State):TCtx = st match {
    case State(_,ectx, aenv, _, _, _, _, _, _) => ectx
  }

  def srcLabelEnvFromState(st:State):Map[Ident,SCtx] = st match {
    case State(_,ectx, aenv, _, _, _, _, _, srcLblEnv) => srcLblEnv
  }

  type AEnv = List[TCtx]
  type EEnv = List[TCtx] 
  type BEnv = List[(TCtx, Option[TCtx])] // when it is None, it means out of the current lexical scope, do we need to keep track of the list of "negative" ctx constructors? 
  type CEnv = List[(TCtx, Option[TCtx])] 


  type ErrorM = String


  sealed trait SSAResult[+A]

  case class SSAError(msg:ErrorM) extends SSAResult[Nothing]
  
  case class SSAOk[A](result:A) extends SSAResult[A]

  implicit def ssaResultFunctor: Functor[SSAResult] =
    new Functor[SSAResult] {
      override def map[A, B](fa: SSAResult[A])(f: A => B): SSAResult[B] =
        fa match {
          case SSAError(s) => SSAError(s)
          case SSAOk(a) => SSAOk(f(a))
        }
    }

  implicit def ssaResultApplicative: ApplicativeError[SSAResult, ErrorM] = 
    new ApplicativeError[SSAResult, ErrorM] {
      override def ap[A, B](ff: SSAResult[A => B])(fa: SSAResult[A]): SSAResult[B] =
        ff match {
          case SSAOk(f) =>
            fa match {
              case SSAOk(a) => SSAOk(f(a))
              case SSAError(s) => SSAError(s)
            }
          case SSAError(s) => SSAError(s)
        }

      override def pure[A](a: A): SSAResult[A] = SSAOk(a)

      override def raiseError[A](e: ErrorM): SSAResult[A] = SSAError(e)

      override def handleErrorWith[A](fa: SSAResult[A])(f: ErrorM => SSAResult[A]): SSAResult[A] =
        fa match {
          case SSAError(s) => f(s)
          case SSAOk(a) => SSAOk(a)
        }
    }

  implicit def ssaResultMonadError(implicit app:ApplicativeError[SSAResult, ErrorM]):MonadError[SSAResult, ErrorM] = {
    new MonadError[SSAResult, ErrorM] {
      override def raiseError[A](e: ErrorM): SSAResult[A] = app.raiseError(e)

      override def handleErrorWith[A](fa: SSAResult[A])(f: ErrorM => SSAResult[A]): SSAResult[A] = app.handleErrorWith(fa)(f)

      override def flatMap[A, B](fa: SSAResult[A])(f: A => SSAResult[B]): SSAResult[B] =
        fa match {
          case SSAOk(a) => f(a)
          case SSAError(s) => SSAError(s)
        }

      override def pure[A](a: A): SSAResult[A] = app.pure(a)

      @annotation.tailrec
      def tailRecM[A, B](init: A)(fn: A => SSAResult[Either[A, B]]): SSAResult[B] =
        fn(init) match {
          case SSAError(msg) => SSAError(msg)
          case SSAOk(Right(b)) => SSAOk(b)
          case SSAOk(Left(a)) => tailRecM(a)(fn)
        }
    }
  }

  type SState[S,A] = StateT[SSAResult, S, A]
  type SSAState[A] = SState[State, A]


  def get:SState[State, State] = StateT { state => SSAOk((state, state))} 

  def put(st:State):SState[State, Unit] = StateT { _ => SSAOk((st,()))} 

  /**
    * setECtx - setting the exiting context in the state
    *
    * @param tctx
    * @param m
    * @return
    */
  def setECtx(tctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get
    st1 <- m.pure(st match {
      case State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs, srcLabelEnv) => State(vm, tctx, aenv, eenv, benv, cenv, nestedDecls, methInvs, srcLabelEnv)
    })
    _   <- put(st1)
  } yield ()


  /**
    * setVM - set the given VarMap in the state
    *
    * @param vm
    * @param m
    * @return
    */
  def setVM(vm:VarMap)(implicit m:MonadError[SSAState, ErrorM]): SState[State, Unit] = for {
    st <- get
    st1 <- m.pure(st match {
      case State(_,eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs, srcLabelEnv) => State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs,srcLabelEnv)
    })
    _   <- put(st1)
  } yield ()
  
  def removeVarFromVM(v:Name)(implicit m:MonadError[SSAState, ErrorM]): SState[State, Unit] = for {
    st <- get
    st1 <- m.pure(st match {
      case State(vm ,eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs, srcLabelEnv) => State(vm - v, eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs,srcLabelEnv)
    })
    _ <- put(st1)
  } yield ()

  /**
    * addNestedVarDecls - add an entry to the nested var decls in the state
    *
    * @param tctx
    * @param id
    * @param ty
    * @param mods
    * @param m
    * @return
    */
  def addNestedVarDecls(tctx:TCtx, id:Ident, ty:Type, mods:List[Modifier])(implicit m:MonadError[SSAState, ErrorM]):SState[State,Unit] = for {
    st <- get
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val nDecls1 = (nDecls.toSet + ((tctx, id, ty, mods))).toList
        State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls1, methInvs,srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()

  /**
    * addMethodInv - add an entry of the method invocatoin in the state
    *
    * @param tctx
    * @param methinv
    * @param m
    * @return
    */
  def addMethodInv(tctx:TCtx, methinv:MethodInvocation)(implicit m:MonadError[SSAState, ErrorM]):SState[State,Unit] = for {
    st <- get 
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val methInvs1 = (methInvs.toSet + ((tctx, methinv))).toList
        State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs1, srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()

  def addSrcLabel(label:Ident, ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get
    st1 <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val srcLblEnv1 = srcLblEnv + (label -> ctx) 
        State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv1)
      }
    })
  } yield ()


  /**
    * addAEnv - add an context to the list of all program context env
    *
    * @param tctx 
    * @param m
    * @return
    */
  def addAEnv(tctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get 
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val aenv1 = (aenv.toSet + tctx).toList
        State(varMap, eCtx, aenv1, eenv, benv, cenv, nDecls, methInvs, srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()


  /**
    * addEEv - add the given context to the list of throwing context in the state
    *
    * @param tctx
    * @param m
    * @return
  */
  

  def addEEnv(tctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get 
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val eenv1 = (eenv.toSet + tctx).toList
        State(varMap, eCtx, aenv, eenv1, benv, cenv, nDecls, methInvs, srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()

  /**
    * addBEnv - and a pair of target contexts into the break environment
    *
    * @param bctx - context where the break statement is 
    * @param tctx - context where the while/switch statement that the break statement is targeting at
    * @param m
    * @return
    */

  def addBEnv(bctx:TCtx, tctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get 
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val benv1 = (benv.toSet + ((bctx,Some(tctx)))).toList
        State(varMap, eCtx, aenv, eenv, benv1, cenv, nDecls, methInvs, srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()

  /**
    * addCEnv - and a pair of target contexts into the continue environment
    *
    * @param bctx - context where the continue statement is 
    * @param tctx - context where the while statement that the continue statement is targeting at
    * @param m
    * @return
    */


  def addCEnv(bctx:TCtx, tctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Unit] = for {
    st <- get 
    st1  <- m.pure(st match {
      case State(varMap, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => {
        val cenv1 = (cenv.toSet + ((bctx,Some(tctx)))).toList
        State(varMap, eCtx, aenv, eenv, benv, cenv1, nDecls, methInvs, srcLblEnv)
      }
    })
    _  <- put(st1)
  } yield ()




  def mergeState(st1:State, st2:State, st3:State):State = {
    val st12 = mergeState(st1, st2)
    mergeState(st12, st3)  
  }
  

  /** 
   * mergeState - merge two states by taking the vm and ectx from st1,
   * and union the eenv and nDecls
   * */
  def mergeState(st1:State, st2:State):State = (st1, st2) match {
    case (State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nDecls1, methInvs1, srcLblEnv1), State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nDecls2, methInvs2, srcLblEnv2)) => 
      State(unionVarMap(vm1, vm2), eCtx1, (aenv1++aenv2).toSet.toList, (eenv1++eenv2).toSet.toList, (benv1++benv2).toSet.toList, (cenv1++cenv2).toSet.toList, (nDecls1 ++ nDecls2).toSet.toList, (methInvs1 ++ methInvs2).toSet.toList, (srcLblEnv1 ++ srcLblEnv2))
  }

  
  def extendVarsWithContextAndLabel(vars: List[Name], sctx:SCtx, tctx:TCtx, lbl:Label)(implicit m:MonadError[SSAState, ErrorM]):SState[State,Unit] = for {
    st <- get
    st1 <- st match {
      case State(vm0, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => for {
        entries <- vars.traverse(v => for {
          v_lbl <- mkName(v, lbl)
        } yield (v, tctx, sctx, v_lbl ))
      } yield State(entries.foldLeft(vm0)((vm, ent) => ent match {
        case (v, tctx, sctx, v_lbl) => vm.get(v) match {
          case None => vm
          case Some(m) => vm + (v -> (m + (tctx -> (sctx, v_lbl))))
        }
      }), eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv)
    }
    _ <- put(st1)
  } yield ()

  // do we still need this? 
  def extendAllVarsWithContextAndLabel(sctx:SCtx, tctx:TCtx, lbl:Label)(implicit m:MonadError[SSAState, ErrorM]):SState[State,Unit] = for {
    st <- get
    st1 <- st match {
      case State(vm0, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => for {
        entries <- vm0.keySet.toList.traverse(v => for {
          v_lbl <- mkName(v, lbl)
        } yield (v, tctx, sctx, v_lbl ))
      } yield State(entries.foldLeft(vm0)((vm, ent) => ent match {
        case (v, tctx, sctx, v_lbl) => vm.get(v) match {
          case None => vm
          case Some(m) => vm + (v -> (m + (tctx -> (sctx, v_lbl))))
        }
      }), eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv)
    }
    _ <- put(st1)
  } yield ()



  /**
  * mkName - create a new name from an existing name and a label.
  *
  * @param n
  * @param lbl
  * @param m
  * @return
  */
  def mkName(n:Name, lbl:Label)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Name] = n match 
    {
      case Name(Nil) => m.raiseError("SSA construction failed, mkName is applied to an empty name.")
      case Name(ids) => {
        val pre = ids.init
        val x   = ids.last
        val s   = lblToStr(lbl)
        val y   = appIdStr(x, s)
        m.pure(Name(pre++List(y)))
      }
    }

  def lblToStr(lbl:Label):String = lbl.toString()


  implicit val eqTCtx:Eq[TCtx] = new Eq[TCtx]{
    override def eqv(x: TCtx, y: TCtx): Boolean = (x,y) match {
      case (TBox, TBox) => true
      case (TLast(ctx1), TLast(ctx2)) => eqv(ctx1, ctx2)
      case (THead(ctx1), THead(ctx2)) => eqv(ctx1, ctx2)
      case (TTail(ctx1), TTail(ctx2)) => eqv(ctx1, ctx2)
      case (TThen(ctx1), TThen(ctx2)) => eqv(ctx1, ctx2)
      case (TElse(ctx1), TElse(ctx2)) => eqv(ctx1, ctx2)
      case (TIfPostPhi, TIfPostPhi) => true
      case (TWhilePrePhi(b1), TWhilePrePhi(b2)) => b1 == b2
      case (TWhile(ctx1), TWhile(ctx2)) => eqv(ctx1, ctx2)
      case (TWhilePostPhi, TWhilePostPhi) => true
      case (TTry(ctx1), TTry(ctx2)) => eqv(ctx1, ctx2)
      case (TTryPeriPhi, TTryPeriPhi) => true
      case (TCatch(ctx1), TCatch(ctx2)) => eqv(ctx1, ctx2)
      case (TTryPostPhi, TTryPostPhi) => true
      case (_,_) => false 
    }
  }


  // ****************************** implementing isLast start *********************************************************************
  // check whether a context is the last of a sequence, w.r.t. to the list all program contexts in the same immediate lexical scope

  def isLast(tctx:TCtx, aenv:AEnv):Boolean = follow(tctx,aenv) match {
    case None => true 
    case Some(_) => false
  }


  def ifElseEnv(aenv:AEnv):Boolean = aenv match {
    case (TThen(_)) :: tl => true 
    case (TElse(_)) :: tl => true
    case (TIfPostPhi :: tl ) => true
    case _ => false
  }

  def whileEnv(aenv:AEnv):Boolean = aenv match {
    case (TWhile(_)) :: tl => true
    case TWhilePostPhi :: tl => true 
    case TWhilePrePhi(_) :: tl => true
    case _ => false 
  }

  def tryEnv(aenv:AEnv):Boolean = aenv match {
    case TTry(_) :: tl => true
    case TTryPeriPhi :: tl => true
    case TTryPostPhi :: tl => true
    case TCatch(_) :: tl => true 
    case _ => false
  }

  def seqEnv(aenv:AEnv):Boolean = aenv match {
    case THead(_) :: tl => true
    case TTail(_) :: tl => true 
    case _ => false
  }

  def lastEnv(aenv:AEnv):Boolean = aenv match {
    case TLast(_) :: tl => true
    case _ => false
  }


  // isLast(c) == true iff follow(c) == None
  /** follow - get the following program context 
   *
   * follow is only called when tctx is not in eenv ++ dom(benv) ++ dom(cenv)
   */
  
  def follow(tctx:TCtx, aenv:AEnv):Option[TCtx] = tctx match {
    case TBox if ifElseEnv(aenv) => Some(TIfPostPhi)
    case TBox if whileEnv(aenv) => Some(TWhilePostPhi)
    case TBox if tryEnv(aenv) => Some(TTryPostPhi)
    case TBox if seqEnv(aenv) => Some(TTail(TBox))
    case TBox if aenv.length > 0 => Some(TLast(TBox))
    case TBox => None
    case TLast(c) => {
      val daenv = appDec(unTLast, aenv)         
      follow(c,daenv) match {
        case Some(n) => Some(TLast(n))
        case None => None
      }
    }
    case THead(c) => Some(TTail(TBox)) // fast-forward to the tail without stepping through c
    case TTail(c) => {
      val daenv = appDec(unTTail, aenv)
      follow(c, daenv) match {
        case Some(n) => Some(TTail(n))
        case None => None
      }
    }
    case TThen(c) => {
      val daenv = appDec(unTThen, aenv)
      follow(c, daenv) match  {
        case Some(n) => Some(TThen(n))
        case None => Some(TIfPostPhi)
      }
    }
    case TElse(c) => {
      val daenv = appDec(unTElse, aenv)
      follow(c, daenv) match {
        case Some(n) => Some(TElse(n))
        case None => Some(TIfPostPhi)
      }
    }
    case TIfPostPhi => None
    case TTry(c) => {
      val daenv = appDec(unTTry, aenv)
      follow(c, daenv) match {
        case Some(n) => Some(TTry(n))
        case None => Some(TTryPostPhi)
      }
    }
    case TTryPeriPhi => follow(TCatch(TBox), aenv)
    
    case TCatch(c) => {
      val daenv = appDec(unTCatch, aenv)
      follow(c, daenv) match {
        case Some(n) => Some(TCatch(n))
        case None => Some(TTryPostPhi)
      }
    }
    case TTryPostPhi => None
    case TWhile(c) => {
      val daenv = appDec(unTCatch, aenv) 
      follow(c, daenv) match {
        case Some(n) => Some(TWhile(n))
        case None => Some(TWhilePostPhi) // there is no statement between TWhilePrePhi and TWhilePostPhi 
      }
    }
    case TWhilePrePhi(_) => Some(TWhilePostPhi)
    case TWhilePostPhi => None
    
  } 


  // list of extractors


  def unTHead(tctx:TCtx):Option[TCtx] = tctx match {
    case THead(c) => Some(c)
    case _        => None
  }

  def unTLast(tctx:TCtx):Option[TCtx] = tctx match {
    case TLast(c) => Some(c) 
    case _        => None
  }

  def unTTail(tctx:TCtx):Option[TCtx] = tctx match {
    case TTail(c) => Some(c)
    case _        => None
  }

  def unTThen(tctx:TCtx):Option[TCtx] = tctx match {
    case TThen(c) => Some(c)
    case _        => None
  } 
   
  def unTElse(tctx:TCtx):Option[TCtx] = tctx match {
    case TElse(c) => Some(c)
    case _        => None
  }

  def unTWhile(tctx:TCtx):Option[TCtx] = tctx match {
    case TWhile(c) => Some(c)
    case _         => None
  }

  def unTTry(tctx:TCtx):Option[TCtx] = tctx match {
    case TTry(c) => Some(c) 
    case _       => None
  }

  def unTCatch(tctx:TCtx):Option[TCtx] = tctx match {
    case TCatch(c) => Some(c)
    case _         => None
  }

  def appDec(dec:TCtx => Option[TCtx], ts:List[TCtx]):List[TCtx] = ts.map(dec(_)).filter( x => !x.isEmpty).flatMap({
    case Some(c) => List(c)
    case None => Nil
  })

  def appDec2(dec:TCtx => Option[TCtx], ts:BEnv):BEnv = ts.flatMap({ 
    case (c1,Some(c2)) => (dec(c1), dec(c2)) match {
      case (Some(c3), Some(c4)) => List((c3,Some(c4)))
      case (Some(c3), None) => List((c3,None))
      case (None, _) => Nil
    }
    case (c1, None) => dec(c1) match {
      case Some(c3) => List((c3, None))
      case None => Nil
    }
  })

  // ****************************** implementing isLast end *********************************************************************



  // ****************************** implementing partial order begin ************************************************************
  // return the domain of a mapping

  def dom[A,B](m:List[(A,B)]):List[A] = m.map{
    case (a,b) => a
  }


  implicit def partialOrderTCtx(aenv:AEnv, eenv:EEnv, benv:BEnv, cenv:CEnv):PartialOrder[TCtx] = new PartialOrder[TCtx]{
    override def partialCompare(x: TCtx, y: TCtx): Double = 
    { 
      (x,y) match {
        case (_,_) if (eqTCtx.eqv(x,y)) => 0.0
        // CtxOrdHole
        case (TBox, _) => -1.0 
        case (_, TBox) => 1.0

        // CtxOrdInd  specialized for Last
        case (TLast(ctx1), TLast(ctx2)) => {
          val daenv = appDec(unTLast, aenv)
          val deenv = appDec(unTLast, eenv)
          val dbenv = appDec2(unTLast, benv) 
          val dcenv = appDec2(unTLast, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }

        // CtxOrdInd specialized for Head
        case (THead(ctx1), THead(ctx2)) => {
          val daenv = appDec(unTHead, aenv)
          val deenv = appDec(unTHead, eenv)
          val dbenv = appDec2(unTHead, benv) 
          val dcenv = appDec2(unTHead, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }

        // CtxOrdSeq
        case (THead(_), TTail(_)) if !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => -1.0

        // CtxOrdInd specialized for TTail
        case (TTail(ctx1), TTail(ctx2)) =>  {
          val daenv = appDec(unTTail, aenv)
          val deenv = appDec(unTTail, eenv)
          val dbenv = appDec2(unTTail, benv) 
          val dcenv = appDec2(unTTail, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }

        // CtxOrdSeq - dual 
        case (TTail(_), THead(_))  => -partialCompare(y,x) 

        // CtxOrdInd specialized for TThen
        case (TThen(ctx1), TThen(ctx2)) => {
          val daenv = appDec(unTThen, aenv)
          val deenv = appDec(unTThen, eenv)
          val dbenv = appDec2(unTThen, benv) 
          val dcenv = appDec2(unTThen, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }
        
        // CtxOrdThen 
        case (TThen(c), TIfPostPhi) if isLast(c, appDec(unTThen, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => -1.0
        case (TThen(c), TIfPostPhi) if isLast(c, appDec(unTThen, aenv)) && ((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => Double.NaN
        // if not last, we need to apply the transtivity
        case (TThen(c), TIfPostPhi) if !isLast(c, appDec(unTThen, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => follow(c, appDec(unTThen, aenv)) match {
          case Some(n) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TThen(n), TIfPostPhi)
          case None  => Double.NaN
        }
        case (TThen(c), TIfPostPhi) if !isLast(c, appDec(unTThen, aenv)) && ((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => Double.NaN

        // CtxOrdInd specialized for TElse
        case (TElse(ctx1), TElse(ctx2)) => {
          val daenv = appDec(unTElse, aenv)
          val deenv = appDec(unTElse, eenv)
          val dbenv = appDec2(unTElse, benv) 
          val dcenv = appDec2(unTElse, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }
        
        // CtxOrdElse 
        case (TElse(c), TIfPostPhi) if isLast(c, appDec(unTElse, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => -1.0
        case (TElse(c), TIfPostPhi) if isLast(c, appDec(unTElse, aenv)) && ((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => Double.NaN
        // if not last, we need to apply the transtivity until we find the last
        case (TElse(c), TIfPostPhi) if !isLast(c, appDec(unTElse, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => follow(c, appDec(unTElse, aenv)) match {
          case Some(n) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TElse(n), TIfPostPhi)
          case None  => Double.NaN
        }
        case (TElse(c), TIfPostPhi) if !isLast(c, appDec(unTElse, aenv)) && ((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => Double.NaN

        // CtxOrdThen - dual 
        case (TIfPostPhi, TThen(c)) => -partialCompare(y,x) 
        // CtxOrdElse - dual 
        case (TIfPostPhi, TElse(c)) => -partialCompare(y,x) 

        // CtxOrdWhileEntry1 
        case (TWhilePrePhi(0), TWhile(_)) => -1.0 // _ or Box? todo: check!!

        // CtxOrdWhileExit2
        case (TWhilePrePhi(_), TWhilePostPhi) => -1.0

        // CtxOrdWhileEntry2
        case (TWhile(c), TWhilePrePhi(1)) if isLast(c, appDec(unTWhile, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => -1.0
        case (TWhile(c), TWhilePrePhi(1)) if isLast(c, appDec(unTWhile, aenv)) && (eenv.contains(x)) => Double.NaN
        case (TWhile(c), TWhilePrePhi(1)) if isLast(c, appDec(unTWhile, aenv)) && (dom(cenv.filter({case ((_,ctx)) => ctx == Some(TBox)})).contains(x)) => -1.0 // CtxOrdWhileEntry3
        case (TWhile(c), TWhilePrePhi(1)) if isLast(c, appDec(unTWhile, aenv)) => Double.NaN   
        // if not last, we need to apply the transtivity
        case (TWhile(c), TWhilePrePhi(1)) if !isLast(c, appDec(unTWhile, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => follow(c, appDec(unTWhile, aenv)) match {
          case Some(n) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TWhile(n), TWhilePrePhi(1))
          case None  => Double.NaN
        }
        case (TWhile(c), TWhilePrePhi(1)) if !isLast(c, appDec(unTWhile, aenv)) && (eenv.contains(x)) => Double.NaN // is this possible?
        case (TWhile(c), TWhilePrePhi(1)) if !isLast(c, appDec(unTWhile, aenv)) && (dom(cenv.filter({case ((_,ctx)) => ctx == Some(TBox)})).contains(x)) => -1.0 // CtxOrdWhileEntry3, is this possible?
        case (TWhile(c), TWhilePrePhi(1)) if !isLast(c, appDec(unTWhile, aenv)) => Double.NaN // is this possible?


        // CtxOrdInd specialized for TWhile
        case (TWhile(ctx1), TWhile(ctx2)) => {
          val daenv = appDec(unTWhile, aenv)
          val deenv = appDec(unTWhile, eenv)
          val dbenv = appDec2(unTWhile, benv) 
          val dcenv = appDec2(unTWhile, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        }



        case (TWhile(c), TWhilePostPhi) if eenv.contains(x) => Double.NaN 
        case (TWhile(c), TWhilePostPhi) if dom((benv ++ cenv).filter({case ((_,ctx)) => ctx != Some(TBox)})).contains(x) => Double.NaN 
        case (TWhile(c), TWhilePostPhi) => -1.0 // (CtxOrdWhileExit1) and (CtxOrdWhileEntry2) (CtxOrdWhileEntry3) with transivitity 
        // CtxOrdWhileExit2 - dual
        case (TWhilePostPhi, TWhilePrePhi(_)) => -partialCompare(y,x) 
        case (TWhilePostPhi, TWhile(c)) => -partialCompare(y,x) 



        // CtxOrdInd specialized for TTry
        case (TTry(ctx1), TTry(ctx2)) => {
          val daenv = appDec(unTTry, aenv)
          val deenv = appDec(unTTry, eenv)
          val dbenv = appDec2(unTTry, benv) 
          val dcenv = appDec2(unTTry, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        } 
        case (TTry(c), TTryPeriPhi) if eenv.contains(x) => -1.0 // (CtxOrdTry1)

        // apply transtivity until we can fire (CtxOrdTry1) or fail at the last 
        case (TTry(c), TTryPeriPhi) if !isLast(c, appDec(unTTry, aenv)) && !((dom(benv) ++ dom(cenv)).contains(x)) => follow(c, appDec(unTTry, aenv)) match {
          case Some(n) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TTry(n), TTryPeriPhi)
          case None  => Double.NaN
        }
        // c must be the last
        case (TTry(c), TTryPeriPhi) => Double.NaN

        case (TTry(c1), TCatch(c2)) if eenv.contains(x) => -1.0 // (CtxOrdTry1) and (CtxOrdCatch1) with transitivity
        // apply transtivity until we can fire previous case or fail at the last 
        case (TTry(c1), TCatch(c2)) if !isLast(c1, appDec(unTTry, aenv)) && !((dom(benv) ++ dom(cenv)).contains(x)) => follow(c1, appDec(unTTry, aenv)) match {
          case Some(n1) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TTry(n1), TCatch(c2))
          case None => Double.NaN
        }
        // c1 must be the last
        case (TTry(c1), TCatch(c2)) => Double.NaN 

        // (CtxOrdTry2), we don't check x is contained in eenv, because even if it is in eenv, we apply transtivity to get the same result
        case (TTry(c), TTryPostPhi) if !dom(benv ++ cenv).contains(x) => -1.0
        case (TTry(c), TTryPostPhi) => Double.NaN

        // dual of the above
        case (TTryPeriPhi, TTry(_)) => -partialCompare(y,x)

        // (CtxOrdCatch1) 
        case (TTryPeriPhi, TCatch(_)) => -1.0
        // (CtxOrdCatch1) and transivitiy, no throw in the catch block
        case (TTryPeriPhi, TTryPostPhi) => partialCompare(TCatch(TBox), TTryPostPhi) // we still need to step through the catch block to ensure no break or continue

        // dual of the above
        case (TCatch(_), TTry(_)) => -partialCompare(y,x)
        case (TCatch(_), TTryPeriPhi) => -partialCompare(y,x)

        // CtxOrdInd specialized for TCatch

        case (TCatch(ctx1), TCatch(ctx2)) => {
          val daenv = appDec(unTCatch, aenv)
          val deenv = appDec(unTCatch, eenv)
          val dbenv = appDec2(unTCatch, benv) 
          val dcenv = appDec2(unTCatch, cenv)
          partialOrderTCtx(daenv, deenv, dbenv, dcenv).partialCompare(ctx1,ctx2)
        } 
        case (TCatch(c), TTryPostPhi) if isLast(c, appDec(unTCatch, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => -1.0 // (CtxOrdCatch2)
        // apply transtivity until we can fire previous case or fail at the last 
        case (TCatch(c), TTryPostPhi) if !isLast(c, appDec(unTCatch, aenv)) && !((eenv ++ dom(benv) ++ dom(cenv)).contains(x)) => follow(c, appDec(unTCatch, aenv)) match {
          case Some(n) => partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(TCatch(n), TTryPostPhi)
          case None => Double.NaN
        }
        case (TCatch(c), TTryPostPhi) => Double.NaN
        // dual of the above
        case (TTryPostPhi, TTry(_)) => -partialCompare(y,x)
        case (TTryPostPhi, TTryPeriPhi) => -partialCompare(y,x)
        case (TTryPostPhi, TCatch(_)) => -partialCompare(y,x)

        case _ => Double.NaN
      }
    }
  }



  // ****************************** implementing partial order end ************************************************************


  /**
  * combine - return the lub from the set
  *
  * @param cs
  * @return
  */
  def combine[A](cs:List[(TCtx,A)], aenv:AEnv, eenv:EEnv, benv:BEnv, cenv:CEnv) :List[(TCtx,A)] = cs match {
    case Nil => Nil
    case x::Nil => x::Nil
    case (x::xs) => {
      val ys = xs.filter(  y => !(partialOrderTCtx(aenv,eenv,benv, cenv).partialCompare(y._1,x._1) == -1.0))
      if (ys.exists( y => partialOrderTCtx(aenv,eenv,benv,cenv).partialCompare(x._1,y._1) == -1.0))
      { 
        combine(ys, aenv, eenv, benv, cenv) 
      }
      else {
        x::combine(ys, aenv, eenv, benv, cenv)
      }
      
    }
  }
  
  
  def Rlt(aenv:AEnv, eenv:EEnv, benv:BEnv, cenv:CEnv, ctx:TCtx, vm:VarMap, x:Name):List[(TCtx,Name)] = { 
    def cmp(p:(TCtx,TCtx)):Boolean = p match {
      case (tctx1, tctx2)  => (partialOrderTCtx(aenv, eenv, benv, cenv).partialCompare(tctx1, tctx2) == -1.0)
    }
    R(aenv, eenv, benv, cenv, ctx, vm, x, cmp)
  }

  def Rleq(aenv:AEnv, eenv:EEnv, benv:BEnv, cenv:CEnv, ctx:TCtx, vm:VarMap, x:Name):List[(TCtx,Name)] = {
    def cmp(p:(TCtx,TCtx)):Boolean = p match {
        case ((tctx1, tctx2))  => { 
          val pot = partialOrderTCtx(aenv, eenv, benv, cenv)
          ((pot.partialCompare(tctx1, tctx2) == -1.0) || (pot.partialCompare(tctx1, tctx2) == 0.0))
        }
      }
    R(aenv, eenv, benv, cenv, ctx, vm, x, cmp)
  }

  /**
    * Compute the name from the lub of all the reachable context until ctx
    *  it defers from the paper, which takes in a default value, we return None in case the set of contexts an empty set. The defaulting should be handled at the call site.
    *
    * @param eenv - exception throwing program contexts
    * @param benv - break statement contexts
    * @param cenv - continue statement contexts
    * @param ctx
    * @param vm
    * @param x
    * @param cmp - modifier to switch between leq or lt
    * @return - return the name of the variable that is the most recent dominator of x
    */
  def R(aenv:AEnv, eenv:EEnv, benv:BEnv, cenv:CEnv, ctx:TCtx, vm:VarMap, x:Name, cmp:((TCtx,TCtx)) => Boolean):List[(TCtx,Name)] = vm.get(x) match { // perhaps we should report the error properly
    case None => Nil
    case Some(trs) => {
      val tcvs = for { 
        (tctx, (sctx, tx)) <- trs.toList
        if (cmp(tctx, ctx))
      } yield (tctx, tx)

      /* not using the builtin semilattice class
      // partial function, but lub should be in the set.
      def comb(px:(TCtx,Name), py:(TCtx,Name)):(TCtx, Name) = (px, py) match {
        case ((cx, vx), (cy, vy)) if (semilatticeTCtx(aenv, eenv, benv, cenv).combine(cx, cy) == cx) => (cx, vx)
        case ((cx, vx), (cy, vy)) if (semilatticeTCtx(aenv, eenv, benv, cenv).combine(cx, cy) == cy) => (cy, vy)
        
      }

      tcvs match {
        case Nil => None
        case (tcv::tcvs) => tcvs.foldLeft(tcv)((x,y) => comb(x,y)) match {
          case (_, vx) => Some(vx)
        }
      }      
      */
      combine(tcvs, aenv,eenv, benv,cenv) 
    }
  }


  /**
    * kmethodDecl - converts a method to SSA method declaration
    *
    * @param md
    * @param m
    * @return
    */
  def kmethodDecl(md:MethodDecl)(implicit m:MonadError[SSAState, ErrorM]):SState[State, SSAMethodDecl] = md match {
    case MethodDecl(modifiers, type_params, return_ty, fname, formal_params, ex_types, exp, body) => body match {
      case MethodBody(None) => m.pure(SSAMethodDecl(modifiers, type_params, return_ty, fname, formal_params, ex_types, exp, SSAMethodBody(Nil)))
      case MethodBody(Some(block)) => {
        val vm = formal_params.foldLeft(Map():VarMap)((vm, param) => {
          vm + ((paramIdtoName(param)) -> Map(TBox -> (SBox, paramIdtoName(param))))
        })
        val lbl = TBox
        for {
          _ <- setVM(vm)
          blocks <- kBlock(block, SBox) 
          _ <- addAEnv(TBox)
          // lbl <- toLbl(TBox) 
          varDeclsStmts <- genVarDecls 
          /*
          varDecls <- m.pure(varDeclsStmts match {
            case Nil => Nil
            case (s::ss) => List(SSABlock(lbl, varDeclsStmts))
            })
            */
        } yield SSAMethodDecl(modifiers, type_params, return_ty, fname, formal_params, ex_types, exp, SSAMethodBody(prependVarDecls(lbl, varDeclsStmts, blocks)))
      }
    }
  }


  /**
    * kstmtBlock - a special version just to handle StmtBlock, when the statement encloses a block of statements, we convert them using kBlock
    *               otherwise, we call kstmt
    *
    * @param stmt
    * @param ctx
    * @param st
    * @return
    */
  
  def kstmtBlock(stmt:Stmt, ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State,List[SSABlock]] = stmt match {
    // case StmtBlock(Block(blkStmts)) => kblkStmts(blkStmts,ctx)
    case StmtBlock(blk) => kBlock(blk,ctx)
    case _ => for {
      b <- kstmt(stmt, ctx)
    } yield List(b)
  }
  

  /**
    * kBlock - convert a block of statements
    *
    * @param blk
    * @param ctx
    * @param m
    * @return
    */
  def kBlock(blk:Block, ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State,List[SSABlock]] = blk match {
    case Block(blkStmts) => kblkStmts(blkStmts,ctx)
  }


  /**
    * kblkStmts - convert a list of block stmts. It is correspondent to the \overline{KS} in the paper.
    *
    * @param blkStmts
    * @param ctx
    * @param m
    * @return
    */

  def kblkStmts(blkStmts:List[BlockStmt], ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, List[SSABlock]] = blkStmts match {
    case Nil => m.pure(Nil)
    case (bstmt::Nil) => for {
      _ <- addAEnv(kctx(ctx))
      b <- kblkStmt(bstmt,putSCtx(ctx, SLast(SBox)))
    } yield List(b)
    case (bstmt::rest) => for {
      _ <- addAEnv(kctx(ctx))
      b <- kblkStmt(bstmt,putSCtx(ctx, SHead(SBox)))
      bs <- kblkStmts(rest, putSCtx(ctx, STail(SBox)))
    } yield (b::bs)
  }


  /**
    * kblkStmt - convert a block statement, it does not handle local class. LocalVars are converted into a SSAVarDecls which is not mentioned in the paper.
    *
    * @param blkStmt
    * @param ctx
    * @param m
    * @return
    */
  def kblkStmt(blkStmt:BlockStmt, ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State,SSABlock] = blkStmt match {
    case BlockStmt_(stmt) => kstmt(stmt, ctx) 
    case LocalClass(_) => m.raiseError("SSA construction failed, local class is not supported.")
    case LocalVars(mods, ty, varDecls) => kVarDecls(mods, ty, varDecls, ctx)
  }


  /**
    * kVarDecls - converts a var declaration, we convert the initialization like other statement / expression. We also keep track of the declration in the state
    *
    * @param mods
    * @param ty
    * @param varDecls
    * @param ctx
    * @param m
    * @return
    */
  def kVarDecls(mods:List[Modifier], ty:Type, varDecls:List[VarDecl], ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, SSABlock] = for {
    tctx <- m.pure(kctx(ctx))
    // a new case, combining KVD and KSTMT assignment
    // we first record all the variable name and type
    _ <- recordVarDecls(mods, ty, varDecls, tctx)
    // we then convert varDecls to SSAVarDecl, hm... what about array init?
    // array id should not be renamed and should not be merged in phis, we keep them inplace as 
    // lbl <- toLbl(tctx)
    varDecls1 <- kVarDecls(varDecls, tctx)
  } yield SSABlock(tctx, List(SSAVarDecls(mods, ty, varDecls1)))
  
  def recordVarDecls(mods:List[Modifier], ty:Type, varDecls:List[VarDecl], tCtx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State,Unit] = varDecls match {
    case Nil => m.pure(())
    case (varDecl::rest) => varDecl match {
      case VarDecl(VarId(id), v_init) => for {
        _ <- addNestedVarDecls(tCtx, id, ty, mods)
        _ <- recordVarDecls(mods, ty, rest, tCtx)
      } yield ()
      case VarDecl(_, v_init) => // it is an array declaration, we don't need to record it as array id will never be merged in phis.
        recordVarDecls(mods, ty, rest, tCtx)
    }
  }

  def kVarDecls(varDecls:List[VarDecl], tCtx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State,List[VarDecl]] = varDecls match {
    case Nil => m.pure(Nil)
    case (varDecl::rest) => varDecl match {
      case VarDecl(VarId(id), ov_init) => for {
        // lbl <- toLbl(tCtx)
        id1 <- mkId(id, tCtx)
        ov_init1 <- ov_init match {
          case None => m.pure(None)
          case Some(v_init) => for { 
            vi1 <- kVarInit(v_init,tCtx)
          } yield Some(vi1)
        }
        rest1 <- kVarDecls(rest, tCtx)
      } yield (VarDecl(VarId(id1), ov_init1)::rest1)
      // array init, we do not rename the lhs ID, we only convert the referenced ids on the RHS
      case VarDecl(VarDeclArray(var_decl_id), ov_init) => for {
        ov_init1 <- ov_init match {
          case None => m.pure(None)
          case Some(v_init) => for {
            vi1 <- kVarInit(v_init, tCtx)
          } yield Some(vi1)
        }
        rest1 <- kVarDecls(rest, tCtx)
      } yield (VarDecl(VarDeclArray(var_decl_id), ov_init1)::rest1)
    }
  }

  def kVarInit(vInit:VarInit, tCtx:TCtx)(implicit m:MonadError[SSAState, ErrorM]): SState[State, VarInit] = vInit match {
    case InitExp(exp) => for {
      exp1 <- kexp(exp, tCtx) 
    } yield InitExp(exp1)
    case InitArray(ArrayInit(var_inits)) => for {
      var_inits1 <- var_inits.traverse(vi => kVarInit(vi, tCtx))
    } yield InitArray(ArrayInit(var_inits1))
  }


  /**
    * kexp - converts an expression, correspondent to the KE function in the paper
    *
    * @param e
    * @param ctx - for keeping track of method invocation location. not used in resolving the most recent variable definition
    * @param m
    * @return
    */

  def kexp(e:Exp, ctx:TCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Exp] = e match {
    case ArrayAccess(ArrayIndex(e, es)) => for {
      e1 <- kexp(e, ctx)
      es1 <- es.traverse(kexp(_, ctx))
    } yield ArrayAccess(ArrayIndex(e1, es1))
    case Cast(ty, exp) => for {
      exp1 <- kexp(exp, ctx) 
    } yield Cast(ty,exp1)
    case ArrayCreate(ty, exps, num_dims) => for {
      exps1 <- exps.traverse(kexp(_, ctx))
    } yield ArrayCreate(ty, exps1, num_dims)
    
    case ArrayCreateInit(ty, size, ArrayInit(v_inits)) => for {
      v_inits1 <- v_inits.traverse(kVarInit(_, ctx))
    } yield ArrayCreateInit(ty, size, ArrayInit(v_inits1))
    case Assign(lhs, op, rhs) => m.raiseError("SSA construction failed, assignment expression should be handled in kstmt.") // might not be true, it does not work with x = (y = 1) + 1 // maybe it should have been flattened or desugared? todo: double check
    case BinOp(e1, op, e2) => for {
      e1p <- kexp(e1, ctx)
      e2p <- kexp(e2, ctx)
    } yield BinOp(e1p, op, e2p)
  
    case ClassLit(ty) => m.pure(e) 
    case Cond(cond, true_exp, false_exp) => for {
      cond1 <- kexp(cond,ctx)
      true_exp1 <- kexp(true_exp, ctx)
      false_exp1 <- kexp(false_exp, ctx)
    } yield Cond(cond1, true_exp1, false_exp1)
    case ExpName(name) => for {
      st <- get
      exp1 <- st match {
        case State(vm, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => Rleq(aenv, eenv, benv, cenv, eCtx, vm, name) match {
          case Nil => m.raiseError(s"SSA construction failed, Rlt failed to find a lub for ${name} during expression conversion. None exists. ${eCtx}, ${vm.toList}")
          case (c,name1)::Nil => m.pure(ExpName(name1))
          case _::_ => m.raiseError(s"SSA construction failed, Rlt failed to find a lub for ${name} during expression conversion. More than one candidates found. ${eCtx}, ${vm.toList}")
        }
      }
    } yield exp1
    
    case FieldAccess_(access) => access match {
      case PrimaryFieldAccess(e, id) => for {
        e1 <- kexp(e, ctx)
      } yield FieldAccess_(PrimaryFieldAccess(e1, id))
      case SuperFieldAccess(id) => m.pure(e)
      case ClassFieldAccess(n, id) => m.pure(e)
    }
    case InstanceCreation(type_args, type_decl, args, body) => 
     
      for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        // not modifying the body. 
      } yield InstanceCreation(type_args, type_decl, args1, body)
    case InstanceOf(e, ref_type) => for {
      e1 <- kexp(e, ctx)
    } yield InstanceOf(e1, ref_type) 
    case Lambda(params, body) => m.raiseError("SSA construction failed, lambda expression is not supported.")
    case Lit(lit) => m.pure(e)
    case MethodInv(methodInv) => methodInv match {
      // method name does not need to be convert
      case MethodCall(name, args) => for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        _ <- addMethodInv(ctx, MethodCall(name, args1))
      } yield MethodInv(MethodCall(name, args1))

      case PrimaryMethodCall(e, ref_types, id, args) => for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        e1 <- kexp(e, ctx)
        _ <- addMethodInv(ctx, PrimaryMethodCall(e1, ref_types, id, args1))
      } yield MethodInv(PrimaryMethodCall(e1, ref_types, id, args1))

      case ClassMethodCall(name, ref_types, id, args) => for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        _ <- addMethodInv(ctx, ClassMethodCall(name, ref_types, id, args1))
      } yield MethodInv(ClassMethodCall(name, ref_types, id, args1))

      case SuperMethodCall(ref_types, id, args) => for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        _ <- addMethodInv(ctx, SuperMethodCall(ref_types, id, args1))
      } yield MethodInv(SuperMethodCall(ref_types, id, args1))

      case TypeMethodCall(name, ref_types, id, args) => for {
        args1 <- args.traverse(arg => kexp(arg, ctx))
        _ <- addMethodInv(ctx, TypeMethodCall(name, ref_types, id, args1))
      } yield MethodInv(TypeMethodCall(name, ref_types, id, args1))
    }
    case MethodRef(name, id) => m.pure(e) 
    case PostDecrement(exp) => m.raiseError("SSA construction failed, PostDecrement expression should have been desugared.")
    case PostIncrement(exp) => m.raiseError("SSA construction failed, PostIncrement expression should have been desugared.")
    case PreBitCompl(exp) => for {
      e1 <- kexp(exp, ctx) 
    } yield PreBitCompl(e1) 
    case PreDecrement(exp) => m.raiseError("SSA construction failed, PreDecrement expression should have been desugared.")
    case PreIncrement(exp) => m.raiseError("SSA construction failed, PreIncrement expression should have been desugared.")
    case PreMinus(exp) => for {
      exp1 <- kexp(exp, ctx)
    } yield PreMinus(exp1) 
    case PreNot(exp) => for {
      exp1 <- kexp(exp, ctx)
    } yield PreNot(exp1) 
    case PrePlus(exp) => for {
      exp1 <- kexp(exp, ctx)
    } yield PrePlus(exp1)
    case QualInstanceCreation(exp, type_args, id, args, body) => m.raiseError("SSA construction failed, Qualified Instance creation expression is not supported.")
    case This => m.pure(e) 
    case ThisClass(name) => m.pure(e) 
  }



  /**
    * kstmt - convert a statement, correspond to KS in the paper.
    *
    * @param stmt
    * @param ctx
    * @param m
    * @return
    */
  

  def kstmt(stmt:Stmt, ctx:SCtx)(implicit m:MonadError[SSAState, ErrorM]):SState[State, SSABlock] = {
    val tctx = kctx(ctx)
    val lbl = tctx
    stmt match {
      case Assert(exp, msg) => for {
        _    <- addAEnv(tctx)
        exp1 <- kexp(exp, tctx)
        msg1 <- msg.traverse( m => kexp(m, tctx))
        _    <- setECtx(tctx)
      } yield SSABlock(lbl, List(SSAAssert(exp1, msg1)))

      case BasicFor(init, loop_cond, post_update, stmt) => m.raiseError("SSA construction failed, BasicFor should have been desugared.")

      case EnhancedFor(modifiers, ty, id, exp, stmt) => m.raiseError("SSA construction failed, EnhancedFor should have been desugared.")

      case Break(None) => m.raiseError("SSA construction failed, Break statement is associated with no label. It should have been pre-processed.")
      case Break(Some(id)) => for {
        st <- get
        r <- st match {
          case State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, metodInvs, srcLabelEnvs) => srcLabelEnvs.get(id) match {         
            case None  => m.raiseError("SSA construction failed. Break statement is associated with a undefined label.")
            case Some(sctx) => for {
              tctx  <- m.pure(kctx(ctx))
              target_tctx <- m.pure(kctx(sctx))
              _          <- addBEnv(tctx, target_tctx)
            } yield SSABlock(lbl, List(SSABreak(target_tctx)))
          }
        }
      } yield r

      case Continue(None) => m.raiseError("SSA construction failed, Continue statement is associated with no label. It should have been pre-processed.")
      case Continue(Some(id)) => for {
        st <- get
        r <- st match {
          case State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, metodInvs, srcLabelEnvs) => srcLabelEnvs.get(id) match {         
            case None  => m.raiseError("SSA construction failed. Continue statement is associated with a undefined label.")
            case Some(sctx) => for {
              tctx  <- m.pure(kctx(ctx))
              target_tctx <- m.pure(kctx(sctx))
              _          <- addCEnv(tctx, target_tctx)
            } yield SSABlock(tctx, List(SSAContinue(target_tctx)))
          }
        }
      } yield r

      case Do(stmt, exp) => m.raiseError("SSA construction failed, Do should have been desguared.")

      case Empty => for {
        _    <- addAEnv(tctx)
        _   <- setECtx(tctx)
      } yield (SSABlock(lbl, List(SSAEmpty)))

      case ExpStmt(Assign(lhs, op, rhs)) => lhs match {
        case NameLhs(x) => for {
          st <- get
          _  <- addAEnv(tctx)
          b <- st match {
            case State(vm, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => for {
              rhs1 <- kexp(rhs, tctx)
              xlbl <- mkName(x,lbl)
              vm1 <- m.pure(vm.get(x) match {
                case None => vm + (x -> Map(tctx -> (ctx, xlbl)))
                case Some(im) => vm + (x -> (im + (tctx -> (ctx, xlbl))))
              })
              _ <- setVM(vm1)
            } yield SSABlock(lbl, List(SSAAssignments(List(ExpStmt(Assign(NameLhs(xlbl), op, rhs1))))))
          }
          _    <- setECtx(tctx)        
        } yield b

        case FieldLhs(fa) => fa match {
          case PrimaryFieldAccess(e1, id) => for {
            _  <- addAEnv(tctx)
            rhs1 <- kexp(rhs, tctx)
            e2  <- kexp(e1, tctx)
            _    <- setECtx(tctx)
          } yield SSABlock(lbl, List(SSAAssignments(List(ExpStmt(Assign(FieldLhs(PrimaryFieldAccess(e2, id)), op, rhs1))))))
          case SuperFieldAccess(id) => for {
            _  <- addAEnv(tctx)
            rhs1 <- kexp(rhs, tctx)
            _    <- setECtx(tctx)
          } yield SSABlock(lbl, List(SSAAssignments(List(ExpStmt(Assign(FieldLhs(SuperFieldAccess(id)), op, rhs1))))))
          case ClassFieldAccess(name, id) => for {
            _  <- addAEnv(tctx)
            rhs1 <- kexp(rhs, tctx)
            _    <- setECtx(tctx)
          } yield SSABlock(lbl, List(SSAAssignments(List(ExpStmt(Assign(FieldLhs(ClassFieldAccess(name,id)), op, rhs1))))))
        }

        case ArrayLhs(ArrayIndex(e,es)) => for {
          _  <- addAEnv(tctx)
          rhs1 <- kexp(rhs, tctx)
          e1   <- kexp(e, tctx)
          es1 <- es.traverse( e => kexp(e, tctx))
          _    <- setECtx(tctx)
        } yield SSABlock(lbl, List(SSAAssignments(List(ExpStmt(Assign(ArrayLhs(ArrayIndex(e1,es1)), op, rhs1))))))
        
      }

      
      /* we move it to kexp, see commented SSAMethodInvcation for details 
      case ExpStmt(MethodInv(methodInv)) => methodInv match {
        // method name does not need to be convert
        case MethodCall(name, args) => for {
          lbl <- toLbl(tctx)
          args1 <- args.traverse(arg => kexp(arg, tctx))
        } yield SSABlock(lbl, SSAMethodInvocation(MethodCall(name, args1)))

        case PrimaryMethodCall(e, ref_types, id, args) => for {
          lbl <- toLbl(tctx)
          args1 <- args.traverse(arg => kexp(arg, tctx))
          e1 <- kexp(e, tctx)
        } yield SSABlock(lbl, SSAMethodInvocation(PrimaryMethodCall(e1, ref_types, id, args1)))

        case ClassMethodCall(name, ref_types, id, args) => for {
          lbl <- toLbl(tctx)
          args1 <- args.traverse(arg => kexp(arg, tctx))
        } yield SSABlock(lbl, SSAMethodInvocation(ClassMethodCall(name, ref_types, id, args1)))

        case SuperMethodCall(ref_types, id, args) => for {
          lbl <- toLbl(tctx)
          args1 <- args.traverse(arg => kexp(arg, tctx))
        } yield SSABlock(lbl, SSAMethodInvocation(SuperMethodCall(ref_types, id, args1)))

        case TypeMethodCall(name, ref_types, id, args) => for {
          lbl <- toLbl(tctx)
          args1 <- args.traverse(arg => kexp(arg, tctx))
        } yield SSABlock(lbl, SSAMethodInvocation(TypeMethodCall(name, ref_types, id, args1)))
      }*/

      case ExpStmt(exp) => for {
        _  <- addAEnv(tctx)
        exp1 <- kexp(exp, tctx)
        _    <- setECtx(tctx)
      } yield SSABlock(lbl, List(SSAExps(List(ExpStmt(exp1)))))

      case IfThen(exp, stmt) => m.raiseError("SSA construction failed, If then statment should have been desugared.")

      
      case IfThenElse(exp, then_stmt, else_stmt) => for {
        _  <- addAEnv(tctx)
        exp1       <- kexp(exp, tctx)
        // reset the eenv in the state 
        st         <- get
        stThenIn   <- st match {
          case State(vm, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => m.pure(State(vm, eCtx, aenv, Nil, benv, cenv, nDecls, methInvs, srcLblEnv))
        }
        _          <- put(stThenIn)
        then_ctx   <- m.pure(putSCtx(ctx, SThen(SBox)))
        then_stmts <- kstmtBlock(then_stmt, then_ctx)
        stThenOut  <- get
        stElseIn   <- st match {
          case State(vm, eCtx, aenv, eenv, benv, cenv, nDecls, methInvs, srcLblEnv) => m.pure(State(vm, eCtx, aenv, Nil, benv, cenv, nDecls, methInvs, srcLblEnv))
        }
        _          <- put(stElseIn)
        else_ctx   <- m.pure(putSCtx(ctx,SElse(SBox)))
        else_stmts <- kstmtBlock(else_stmt, else_ctx)
        stElseOut  <- get
        stMerged   <- m.pure(mergeState(st, stThenOut, stElseOut)) 
        _          <- put(stMerged)

        tctx2      <- m.pure(putTCtx(tctx, TIfPostPhi))
        lbl2       <- m.pure(tctx2)

        phis       <- mkPhi(st, stThenOut, stElseOut, lbl2)
        _          <- extendVarsWithContextAndLabel(phis.map( ph => ph match {case Phi(n, renamed, m) => n }), ctx, tctx2, lbl2) 
        _          <- setECtx(tctx2)
      } yield SSABlock(lbl, List(SSAIf(exp1, then_stmts, else_stmts, phis)))
      
      case Labeled(id, stmt) => for {
        _ <- addSrcLabel(id, ctx)
        r <- kstmt(stmt,ctx) 
      } yield r

      case Return(oexp) => oexp match {
        case Some(exp) => for {
          _  <- addAEnv(tctx)
          // lbl  <- m.pure(tctx)
          exp1 <- kexp(exp,tctx) 
          _    <- setECtx(tctx)

        } yield SSABlock(lbl, List(SSAReturn(Some(exp1))))
        case None => for {
          _  <- addAEnv(tctx)
          // lbl  <- m.pure(tctx)
          _    <- setECtx(tctx)
        } yield SSABlock(lbl, List(SSAReturn(None)))
      }

      case StmtBlock(blk) => m.raiseError("SSA construction failed, Statement Block should not be handled here.") // todo

      case Switch(exp, blocks) => m.raiseError("SSA construction failed, Switch statement is not supported.") // todo
      case Synchronized(exp, blk) => m.raiseError("SSA construction failed, Synchronized statement is not supported.") // todo

      case Throw(exp) => for {
          _  <- addAEnv(tctx)
        // lbl  <- toLbl(tctx)
        exp1 <- kexp(exp, tctx)
        _    <- addEEnv(tctx)
        _    <- setECtx(tctx)
      } yield SSABlock(lbl, List(SSAThrow(exp1)))

      /* TODO fix me
      case Try(try_blk, Catch(param, catch_blk)::Nil, finally_blk) => finally_blk match {
        case Some(b) => m.raiseError("SSA construction failed, Try catch finally should be desugared to Try catch.")
        case None    => for {
          _         <- addAEnv(tctx)
          // lbl       <- toLbl(tctx)
          st        <- get

          stTryIn   <- st match {
            case State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, methInvs, srcLblEnv) => m.pure(State(vm,eCtx,aenv, Nil, benv, cenv, nestedDecls, methInvs, srcLblEnv))
          }
          _         <- put(stTryIn)
          try_ctx   <- m.pure(putSCtx(ctx, STry(SBox)))
          try_stmts <- kBlock(try_blk, try_ctx)
          stTryOut  <- get

          tctx1p    <- m.pure(putTCtx(tctx, TTryPeriPhi))
          lbl1p     <- toLbl(tctx1p)
          phis_peri <- mkPhisFromThrows(stTryOut, eenvFromState(st), benvFromState(st), cenvFromState(st), lbl1p)

          catch_ctx <- m.pure(putSCtx(ctx, SCatch(SBox)))
          catch_tctx <- m.pure(putTCtx(tctx, TCatch(TBox)))
          stCatchIn <- stTryOut match {
            case State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1) => for {
              entries <- vm1.keySet.toList.traverse(v => for {
                v_lbl <- mkName(v, lbl1p)
              } yield (v, ctx, tctx1p, v_lbl))
            } yield State((entries.foldLeft(vm1)((vm, ent) => ent match {
              case (v, sctx, tctx, v_lbl) => vm.get(v) match {
                case None => vm
                case Some(m) => vm + (v -> (m + (tctx -> (sctx, v_lbl))))
              }}) + ((paramIdtoName(param)) -> Map(catch_tctx -> ((ctx, paramIdtoName(param)))))), 
              tctx1p, aenv1, Nil, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1)
            }
          _           <- put(stCatchIn)
          catch_stmts <- kBlock(catch_blk, catch_ctx)
          _          <- removeVarFromVM(paramIdtoName(param))
          stCatchOut <- get
          
          tctx3 <- m.pure(putTCtx(tctx, TTryPostPhi))
          lbl3  <- toLbl(tctx3)

          phis_post <- mkPhi(stTryOut, stCatchOut, lbl3)
          _         <- extendAllVarsWithContextAndLabel(ctx, tctx3, lbl3)
          _         <- setECtx(tctx)
          _         <- eenvFromState(st).traverse( ctx => addEEnv(ctx))
        } yield SSABlock(lbl, List(SSATry(try_stmts, phis_peri, param, catch_stmts, phis_post)))
      } */
      case Try(_, Nil, _) => m.raiseError("SSA construction failed, there is no catch in a try statement")
      case Try(_, _::_, _) => m.raiseError("SSA construction failed, Multiple catch clauses encountered, which should have been merged.")
      case While(exp, stmt) => for {
        _  <- addAEnv(tctx)
        // lbl <- toLbl(tctx)
        st  <- get
        lbl0  <- m.pure(eCtxFromState(st))
        tctx_pre0 <- m.pure(putTCtx(tctx, TWhilePrePhi(0)))
        tctx_pre1 <- m.pure(putTCtx(tctx, TWhilePrePhi(1)))
        // lbl1_0  <- m.pure(tctx_pre0) // not in used, we use option 2
        lbl1  <- m.pure(tctx_pre1)

        phis_pre  <- st match { // phi_bar
          case State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0) 
          if ((eenv0++dom(benv0++cenv0)).contains(eCtx0)) => // is this still possible? it means the while statement is dead code
            vm0.keySet.toList.traverse( v => for {
              v_lbl <- mkName(v, lbl1)
            } yield Phi(v, v_lbl, Map()))
          case State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0) => 
            vm0.keySet.toList.traverse( v => for {
              v_lbl <- mkName(v, lbl1)
              rhs <- Rleq(aenv0, eenv0, benv0, cenv0, eCtx0, vm0, v) match {
                case Nil => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. None found.")
                case (c,n)::Nil => m.pure(Map(lbl0 -> n))
                case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. More than one candidates found.")
              }
            } yield Phi(v, v_lbl, rhs))
        }
        stBodyIn <- st match { // creating vm1
          case State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0) => for {
            entries <- vm0.keySet.toList.traverse( v => for {
              v_lbl <- mkName(v, lbl1)
            } yield (v, tctx_pre0, ctx, v_lbl))
          } yield State(entries.foldLeft(vm0)((vm1, ent) => ent match {
            case (v, tctx2, sctx, v_lbl) => vm1.get(v) match {
              case None => vm1
              case Some(m) => vm1 + (v -> (m + (tctx2  -> (sctx, v_lbl))))
            }}), tctx_pre0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0)
        }
        _ <- put(stBodyIn)
        exp1 <- kexp(exp, tctx)
        body_ctx <- m.pure(putSCtx(ctx, SWhile(SBox)))
        body_stmts <- kstmtBlock(stmt, body_ctx)
        stBodyOut <- get

        phis_pre_updated <- (st, stBodyIn, stBodyOut) match { // phi_bar'
          // todo check the case for break and continue
          case (State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0),
               State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1), 
               State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2)) if ((eenv2 ++ dom(benv2 ++ codexclude(cenv2, tctx))).contains(eCtx2)) => for {
            phis_pre2 <- phis_pre.traverse( phi => updatePhiFromCEnv(phi, stBodyOut, tctx)) // we don't need to set the lower bound for the Ctx, since we have 0 bit set in the vm environment, but what about the nested one?
          } yield phis_pre2
          // the third case from the tech report.
          case (State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0),
                State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1),
                State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2)) => {
            val vs = diffVarMap(vm2,vm1).keySet // dom3(vm2- vm1) // TODO: do we need to exclude those in benv and cenv?
            for {
              phis <- vs.toList.traverse (v => for { 
                v_lbl <- mkName(v, lbl1)
                lbl0  <- m.pure(eCtx0)
                lbl2  <- m.pure(eCtx2)
                name0 <- Rleq(aenv0, eenv0, benv0, cenv0, eCtx0, vm0, v) match {
                  case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub during the while stmt conversion.")
                  case (c,n)::Nil => m.pure(n)
                  case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. More than one candidates found.")          
                }
                name2 <- Rleq(aenv2, eenv2, benv2, cenv2, eCtx2, vm2, v) match {
                  case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub during the while stmt conversion.")
                  case (c,n)::Nil => m.pure(n)
                  case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. More than one candidates found.")            
                }
              } yield Phi(v, v_lbl, Map(lbl0 -> name0, lbl2 -> name2)))
              phis2 <- phis.traverse( phi => updatePhiFromCEnv(phi, stBodyOut, tctx))
            } yield phis2
          }
        }
        subst <- mkSubstFromStates(st, stBodyIn, stBodyOut, lbl1) // theta
        // to be cobntinue here.
        body_stmts_s <- m.pure(body_stmts.map(appSubst(subst, _))) // theta(B)

        // vm3 // todo what about cenv and benv
        vm3 <- (st, stBodyIn, stBodyOut) match { // vm3 
            case (State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0),
                State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1),
                State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2)) => {
                  val vs = (diffVarMap(vm2,vm1)).keySet // dom3(vm2 - vm1)
                  for {
                    entries <- vs.toList.traverse( v => for {
                      v_lbl <- mkName(v, lbl1)
                    } yield (v, tctx_pre1, ctx, v_lbl))
                  } yield entries.foldLeft(vm0 ++ diffVarMap(vm2,vm1))((vm3, ent) => ent match {
                      case (v, tctx11, sctx, v_lbl) => vm3.get(v) match {
                        case None => vm3
                        case Some(m) => vm1 + (v -> (m + (tctx11  -> (sctx, v_lbl))))
                      }
                  }) 
                }
        }

        tctx3 <- m.pure(putTCtx(tctx, TWhilePostPhi))
        lbl3  <- m.pure(tctx3)
        phis_post <- stBodyOut match {
          case State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2) => vm2.keySet.toList.traverse( v => for {
            v_lbl3 <- mkName(v, lbl3)
            v_lbl1 <- mkName(v, lbl1)
          } yield Phi(v, v_lbl3, Map(lbl1 -> v_lbl1)))
        }
        // update with the break statements
        phis_post2 <- phis_post.traverse(phi => updatePhiFromBEnv(phi,stBodyOut,tctx))
        vm3        <- (stBodyIn,stBodyOut) match {
          case (State(vm1,eCtx1,aenv1,eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1), State(vm2,eCtx2,aenv2,eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2)) => {
            val ctxs = eenv2 ++ dom(codexclude(benv2,tctx) ++ codexclude(cenv2,tctx))
            val intervals = ctxs.map( ctx => interval_leq(vm2,aenv2,eenv2,benv2,cenv2,tctx_pre,ctx) ) 
            m.pure(intervals.foldLeft(vm1)((m1,m2) => unionVarMap(m1,m2))) 
          }
        }
        _          <- setVM(vm3)
        _          <- extendAllVarsWithContextAndLabel(ctx, tctx3, lbl3)
        _          <- setECtx(tctx3)
      } yield SSABlock(lbl, List(SSAWhile(phis_pre_updated, exp1, body_stmts_s, phis_post2)))
    }
  } 




  /**
    * mkPhi - create phis by merging all variables found in vmap in st1 and st2, 
    *
    * @param st0 - based state 0
    * @param st1 - incoming state 1
    * @param st2 - incoming state 2
    * @param lbl - phi LHS variable label
    * @param m   - monad type class context
    * @return
    */

  def mkPhi(st0:State, st1:State, st2:State, lbl:Label)(implicit m:MonadError[SSAState, ErrorM]):SState[State, List[Phi]] = (st0, st1, st2) match {
    /*
    case (State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1,  _, _, _), State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, _, _,_)) if ((eenv1 ++ dom(benv1 ++ cenv1)).contains(eCtx1)) && ((eenv2 ++ dom(benv2 ++ cenv2)).contains(eCtx2)) => for {
      // do we still need this case? it means dead code
      vs <- m.pure(vm1.keySet ++ vm2.keySet)
      phis <- vs.toList.traverse( v => for {
        vlbl <- mkName(v, lbl)
      } yield Phi(v, vlbl, Map()))
    } yield phis
    case (State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, _, _,_), State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, _, _,_)) if !((eenv1 ++ dom(benv1 ++ cenv1)).contains(eCtx1)) && ((eenv2 ++ dom(benv2 ++ cenv2)).contains(eCtx2)) => for {
      vs <- m.pure(vm1.keySet)
      phis <- vs.toList.traverse( v => for {
        vlbl <- mkName(v, lbl)
        lbl1 <- toLbl(eCtx1)
        name <- Rleq(aenv1, eenv1, benv1, cenv1, eCtx1, vm1, v) match {
          case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub in mkPhi() for ${v}. None exists. ${vm1}")
          case (c,n)::Nil => m.pure(n)
          case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub in mkPhi(). More than one candidates found.")
        }
      } yield Phi(v, vlbl, Map(lbl1 -> name)))  
    } yield phis
    case (State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, _, _, _), State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2,_, _, _)) if ((eenv1 ++ dom(benv1 ++ cenv1)).contains(eCtx1)) && !((eenv2 ++ dom(benv2 ++ cenv2)).contains(eCtx2)) => for {
      vs <- m.pure(vm2.keySet)
      phis <- vs.toList.traverse( v => for {
        vlbl <- mkName(v, lbl)
        lbl2 <- toLbl(eCtx2)
        name <- Rleq(aenv2, eenv2, benv2, cenv2, eCtx2, vm2, v) match {
          case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub in mkPhi() for ${v}. None exists. ${vm2}")
          case (c,n)::Nil => m.pure(n)
          case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub in mkPhi(). More than one candidates found.")          
        }
      } yield Phi(v, vlbl, Map(lbl2 -> name)))
    } yield phis
    */  
    case (State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, _, _, _), State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, _, _, _), State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2,_, _, _)) => for {
      vs <- m.pure((diffVarMap(vm1,vm0) ++ diffVarMap(vm2,vm0)).keySet)
      phis <- vs.toList.traverse( v => for {
        vlbl <- mkName(v, lbl)
        lbl1 <- m.pure(eCtx1)
        lbl2 <- m.pure(eCtx2)
        name1 <- Rleq(aenv1, eenv1, benv1, cenv1, eCtx1, vm1, v) match {
          case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub in mkPhi() for ${v}. None exists. ${vm1}")
          case (c,n)::Nil => m.pure(n)
          case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub in mkPhi(). More than one candidates found.")          
        }
        name2 <- Rleq(aenv2, eenv2, benv2, cenv2, eCtx2, vm2, v) match {
          case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub in mkPhi() for ${v}. None exists. ${vm2}")
          case (c,n)::Nil => m.pure(n)
          case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub in mkPhi(). More than one candidates found.")            
        }
      } yield Phi(v, vlbl, Map(lbl1 -> name1, lbl2 -> name2)))
    } yield phis  
  }



  /**
    * genVarDecls - generate the list of statements from the nested declaration collected during the conversion.
    *
    * @param m
    * @return
    */    
  def genVarDecls(implicit m:MonadError[SSAState, ErrorM]):SState[State, List[SSAStmt]] = for {
    st <- get
    stmts <- st match {
      case State(vm, eCtx, aenv, eenv, benv, cenv, nestedDecls, methodInvs, srcLblEnv) => for {
        tbl <- m.pure(mkTable(nestedDecls)) 
        ll  <- vm.toList.traverse( p => p match {
          case (x, ctxm) => tbl.get(x) match {
            case None => m.pure(Nil:List[SSAStmt])
            case Some((ty,mods)) => { 
              def go(q:(TCtx, (SCtx, Name))):SState[State, SSAStmt] = q match {
                case (tctx, (ctx, xlbl@Name(List(id)))) => {
                  m.pure(SSAVarDecls(mods, ty, List(VarDecl(VarId(id), None))))
                }
                case (tctx, (ctx, xlbl)) => {
                  m.raiseError("SSA construction failed, genVarDecls - the renamed variable is a not a simple id.")
                }
              }
              ctxm.toList.traverse(go(_))
            }
          }
        })
      } yield ll.flatMap( p => p)
    }
  } yield stmts

  /**
    * Turn the list of nested declaration into a hash map (name -> (Type, [Modifier]))
    *
    * @param nestedDecls
    * @return
    */
  def mkTable(nestedDecls: List[(TCtx, Ident, Type, List[Modifier])]):Map[Name, (Type, List[Modifier])] = 
    nestedDecls.foldLeft(Map():Map[Name, (Type, List[Modifier])])( (m, ndecl) => ndecl match {
      case (_, id, ty, mods) => m + (Name(List(id)) -> (ty,mods))
    })

  /**
      * paramIdtoName - turn a formal param into a Name
      *
      * @param fp
      * @return
      */
  def paramIdtoName(fp:FormalParam):Name = fp match {
    case FormalParam(mods, ty, arity, var_decl_id) => Name(List(idFromVarDeclId(var_decl_id)))
  }


  /**
    * kctx - converts a source program context into a target program context
    *
    * @param ctx
    * @return
    */

  def kctx(ctx:SCtx):TCtx = ctx match {
    case SBox => TBox
    case SLast(ctx1) => TLast(kctx(ctx1))
    case SHead(ctx1) => THead(kctx(ctx1))
    case STail(ctx1) => TTail(kctx(ctx1))
    case SThen(ctx1) => TThen(kctx(ctx1))
    case SElse(ctx1) => TElse(kctx(ctx1))
    case SWhile(ctx1) => TWhile(kctx(ctx1))
    case STry(ctx1) => TTry(kctx(ctx1))
    case SCatch(ctx1) => TCatch(kctx(ctx1))
  } 

  /**
    * mkId - create a new ID from an existing id and a label
    *
    * @param id
    * @param lbl
    * @param m
    * @return
    */
  def mkId(id:Ident, lbl:Label)(implicit m:MonadError[SSAState, ErrorM]):SState[State, Ident] = {
    val s = lblToStr(lbl)
    val y = appIdStr(id,s)
    m.pure(y)
  }


  // exclude entries based on a value in the codomain

  def codexclude[A,B](m:List[(A,Option[B])],v:B):List[(A,Option[B])] = m.flatMap{
    case (a,None) => List((a,None))
    case (a,Some(b)) if b == v => Nil
    case (a,Some(b)) => List((a,Some(b)))
  }

  /**
    * update the given phi based on the continue environment. 
    *
    * @param phi
    * @param st
    * @param parentctx
    * @param lctx
    * @param m
    * @return
    */

  def updatePhiFromCEnv(phi:Phi, st:State, parentctx:TCtx)(implicit m:MonadError[SSAState,ErrorM]):SState[State,Phi] = (st,phi) match {
      case (State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2), Phi(v,v_vlbl,rhs_map)) => { 
      {
        def go(ctxk:TCtx):SState[State, (Label, Name)] = for {
          lbl_k <- m.pure(ctxk)
          name <- Rleq(aenv2, eenv2, benv2, cenv2, ctxk, vm2, v) match {
            case Nil => m.pure(v_vlbl) // as default, since the set could be empty, which still lattice
            case ((c,n)::Nil) => m.pure(n)
            case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. More than one candidates found.")
          }
        } yield (lbl_k,name)
        val cenv2_filtered_dom = cenv2.filter( (x:(TCtx,Option[TCtx])) => x._2 == Some(parentctx)).map(p=>p._1)
        for { 
          rhs_tb_added <- cenv2_filtered_dom.traverse(go)
          rhs_map_updated <- m.pure(rhs_tb_added.foldLeft(rhs_map)((m,p)=>m + (p._1 -> p._2)))
        } yield Phi(v, v_vlbl, rhs_map_updated)
      }
    }
  }

  /**
    * make substitution for while conversion
    *
    * @param st0 - input state to the entire statement
    * @param st1 - input state to the while body
    * @param st2 - output state from the while body
    * @param lbl1 - preWhilePhi^0 
    * @param m
    * @return
    */

  def mkSubstFromStates(st0:State, st1:State, st2:State, lbl1:Label)(implicit m:MonadError[SSAState,ErrorM]):SState[State,Map[Name,Name]] = (st0, st1, st2) match { 
    // todo check the case for break and continue // no need?
    // 
    case (State(vm0, eCtx0, aenv0, eenv0, benv0, cenv0, nestedDecls0, methInvs0, srcLblEnv0),
          State(vm1, eCtx1, aenv1, eenv1, benv1, cenv1, nestedDecls1, methInvs1, srcLblEnv1),
          State(vm2, eCtx2, aenv2, eenv2, benv2, cenv2, nestedDecls2, methInvs2, srcLblEnv2)) => {
            val vs = diffVarMap(vm2, vm1).keySet // dom3(vm2 - vm1)
            val no_update = vm0.keySet -- vs
            for {
              ls <- no_update.toList.traverse(v => for {
                v_lbl1 <- mkName(v,lbl1) // the v_l1 to be renamed back to the original
                v_ori <- Rleq(aenv0, eenv0, benv0, cenv0, eCtx0, vm0, v) match {
                  // case Nil => m.raiseError(s"SSA construction failed, Rleq failed to find a lub during the while stmt conversion.")
                  case (c,n)::Nil => m.pure(n)
                  // case _::_ => m.raiseError("SSA construction failed, Rleq failed to find a lub during the while stmt conversion. More than one candidates found.")          
                }
              } yield (v_lbl1, v_ori))
           } yield ls.foldLeft(Map():Map[Name,Name])((m,p:(Name,Name)) => (m + (p._1 -> p._2)))
    }
  }

  // TODO
  def appSubst(subst:Map[Name,Name], b:SSABlock): SSABlock = b



}
