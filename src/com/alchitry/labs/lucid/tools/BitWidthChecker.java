package com.alchitry.labs.lucid.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.alchitry.labs.Util;
import com.alchitry.labs.gui.MainWindow;
import com.alchitry.labs.language.ConstValue;
import com.alchitry.labs.language.InstModule;
import com.alchitry.labs.language.Module;
import com.alchitry.labs.language.Param;
import com.alchitry.labs.language.Sig;
import com.alchitry.labs.lucid.AssignBlock;
import com.alchitry.labs.lucid.Connection;
import com.alchitry.labs.lucid.Lucid;
import com.alchitry.labs.lucid.SignalWidth;
import com.alchitry.labs.lucid.Struct;
import com.alchitry.labs.lucid.parser.LucidBaseListener;
import com.alchitry.labs.lucid.parser.LucidParser.Array_indexContext;
import com.alchitry.labs.lucid.parser.LucidParser.Array_sizeContext;
import com.alchitry.labs.lucid.parser.LucidParser.Assign_statContext;
import com.alchitry.labs.lucid.parser.LucidParser.BitSelectorConstContext;
import com.alchitry.labs.lucid.parser.LucidParser.BitSelectorFixWidthContext;
import com.alchitry.labs.lucid.parser.LucidParser.Bit_selectionContext;
import com.alchitry.labs.lucid.parser.LucidParser.Const_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.Dff_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.Dff_singleContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprAddSubContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprAndOrContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprArrayContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprCompareContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprCompressContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprConcatContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprDupContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprFunctionContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprGroupContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprInvertContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprLogicalContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprMultDivContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprNegateContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprNumContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprShiftContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprSignalContext;
import com.alchitry.labs.lucid.parser.LucidParser.ExprTernaryContext;
import com.alchitry.labs.lucid.parser.LucidParser.Fsm_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.FunctionContext;
import com.alchitry.labs.lucid.parser.LucidParser.Inout_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.Input_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.ModuleContext;
import com.alchitry.labs.lucid.parser.LucidParser.Module_instContext;
import com.alchitry.labs.lucid.parser.LucidParser.NameContext;
import com.alchitry.labs.lucid.parser.LucidParser.NumberContext;
import com.alchitry.labs.lucid.parser.LucidParser.Output_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.Param_nameContext;
import com.alchitry.labs.lucid.parser.LucidParser.Sig_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.SignalContext;
import com.alchitry.labs.lucid.parser.LucidParser.SourceContext;
import com.alchitry.labs.lucid.parser.LucidParser.Struct_typeContext;
import com.alchitry.labs.lucid.parser.LucidParser.Type_decContext;
import com.alchitry.labs.lucid.parser.LucidParser.Var_decContext;
import com.alchitry.labs.lucid.style.TokenErrorListener;

public class BitWidthChecker extends LucidBaseListener implements WidthProvider {
	private LucidErrorChecker errorChecker;
	private ConstExprParser constExprParser;
	private ConstProvider constParser;
	private BoundsProvider boundsProvider;

	private HashMap<String, SignalWidth> widthMap = new HashMap<>();
	protected ParseTreeProperty<SignalWidth> widths;

	private InstModule currentModule;

	public BitWidthChecker(LucidErrorChecker errorListener, ConstExprParser p) {
		errorChecker = errorListener;
		constExprParser = p;
	}

	public void setConstParser(ConstProvider constParser) {
		this.constParser = constParser;
	}

	public void setBoundsProvider(BoundsProvider bp) {
		boundsProvider = bp;
	}

	private void debug(ParserRuleContext ctx) {
		// errorChecker.onTokenDebugFound(ctx, ctx.getText() + " = " + (widths.get(ctx) == null ? "null" : widths.get(ctx).toString()));
	}

	private void debugNullConstant(ParserRuleContext ctx) {
		// errorChecker.onTokenDebugFound(ctx, "This should have a known value!");
	}

	@Override
	public void enterSource(SourceContext ctx) {
		widths = new ParseTreeProperty<SignalWidth>();
	}

	@Override
	public void enterModule(ModuleContext ctx) {
		widthMap.clear();
	}

	private void checkSimpleArray(SignalWidth w) {
		if (w != null)
			w.assertSimpleArray();
	}

	public BoundsProvider getBoundsProvider() {
		return boundsProvider;
	}

	public SignalWidth getArrayWidth(List<Array_sizeContext> ctx, Struct_typeContext sctx) {
		SignalWidth width = new SignalWidth();
		for (Array_sizeContext asc : ctx) {
			SignalWidth aw = widths.get(asc);
			if (aw != null && !aw.isStruct())
				width.getWidths().add(aw.getWidths().get(0));
		}

		if (sctx != null) {
			Struct s = null;
			if (sctx.name().size() == 1)
				s = errorChecker.getStruct(sctx.name().get(0).getText());
			if (sctx.name().size() == 2) {
				HashMap<String, List<Struct>> gS = MainWindow.getOpenProject().getGlobalStructs();
				List<Struct> structs = gS.get(sctx.name(0).getText());
				if (structs != null) {
					s = Util.getByName(structs, sctx.name(1).getText());
				} else {
					errorChecker.onTokenErrorFound(sctx, String.format(ErrorStrings.UNKNOWN_NAMESPACE, sctx.name(0).getText()));
				}
			}

			if (s != null) {
				SignalWidth sw = new SignalWidth(s);
				if (width.getDepth() == 0)
					width = sw;
				else
					width.setNext(sw);
			} else {
				errorChecker.onTokenErrorFound(sctx.name(sctx.name().size() - 1), String.format(ErrorStrings.UNKNOWN_STRUCT, sctx.name(sctx.name().size() - 1).getText()));
			}

		}

		if (width.getDepth() == 0) {
			width.getWidths().add(1);
		}
		return width;
	}

	@Override
	public void exitFunction(FunctionContext ctx) {
		if (ctx.expr() == null || ctx.FUNCTION_ID() == null)
			return;

		String sfid = ctx.FUNCTION_ID().getText();

		ConstValue cv = constExprParser.getValue(ctx);

		switch (sfid) {
		case "$clog2":
		case "$pow":
		case "$cdiv":
			if (cv != null)
				widths.put(ctx, cv.getArrayWidth());
			break;
		case "$flatten":
			if (cv != null)
				widths.put(ctx, cv.getArrayWidth());
			else if (ctx.expr().size() == 1) {
				SignalWidth aw = widths.get(ctx.expr(0));
				if (aw != null)
					widths.put(ctx, aw.flatten());
			}

			break;
		case "$reverse":
		case "$unsigned":
		case "$signed":
			if (ctx.expr().size() == 1)
				widths.put(ctx, widths.get(ctx.expr(0)));
			break;
		default:
			Util.log.severe("Unknown function " + sfid + " in BitWidthChecker.");
			break;
		}

	}

	@Override
	public void exitInput_dec(Input_decContext ctx) {
		if (ctx.name().TYPE_ID() != null) {
			SignalWidth width = getArrayWidth(ctx.array_size(), ctx.struct_type());
			widthMap.put(ctx.name().getText(), width);
			widths.put(ctx, width);
		}
	};

	@Override
	public void exitOutput_dec(Output_decContext ctx) {
		if (ctx.name() != null) {
			SignalWidth width = getArrayWidth(ctx.array_size(), ctx.struct_type());
			widthMap.put(ctx.name().getText(), width);
			widths.put(ctx, width);
		}
	}

	@Override
	public void exitInout_dec(Inout_decContext ctx) {
		if (ctx.name() != null) {
			SignalWidth width = getArrayWidth(ctx.array_size(), ctx.struct_type());
			widthMap.put(ctx.name().getText() + ".enable", width);
			widthMap.put(ctx.name().getText() + ".read", width);
			widthMap.put(ctx.name().getText() + ".write", width);
			widths.put(ctx, width);
		}
	}

	@Override
	public void exitParam_name(Param_nameContext ctx) {
		if (ctx.name() != null && ctx.expr() != null && widths.get(ctx.expr()) != null) {
			if (widthMap.get(ctx.name().getText()) == null) {
				SignalWidth width = new SignalWidth(widths.get(ctx.expr()));
				widthMap.put(ctx.name().getText(), width);
			}
		}
	};

	@Override
	public void exitConst_dec(Const_decContext ctx) {
		if (ctx.name() != null && ctx.expr() != null && widths.get(ctx.expr()) != null) {
			SignalWidth width = new SignalWidth(widths.get(ctx.expr()));
			widthMap.put(ctx.name().getText(), width);
			widths.put(ctx, width);
		}
	}

	@Override
	public void exitSig_dec(Sig_decContext ctx) {
		if (ctx.type_dec() != null)
			for (Type_decContext dc : ctx.type_dec()) {
				if (dc.name() != null) {
					SignalWidth width = getArrayWidth(dc.array_size(), ctx.struct_type());
					widthMap.put(dc.name().getText(), width);
					widths.put(dc, width);
				}
			}
	}

	@Override
	public void exitVar_dec(Var_decContext ctx) {
		for (Type_decContext dc : ctx.type_dec()) {
			if (dc.name() != null) {
				if (dc.name() != null) {
					SignalWidth width = getArrayWidth(dc.array_size(), null);
					widthMap.put(dc.name().getText(), getArrayWidth(dc.array_size(), null));
					widths.put(dc, width);
				}
			}
		}
	}

	private ConstProvider paramsProvider = new ConstProvider() {

		@Override
		public ConstValue getValue(String s) {
			for (AssignBlock block : errorChecker.getAssignBlock()) {
				if (block != null) {
					for (Connection c : block.connections) {
						if (c.param && c.port.equals(s)) {
							return c.value;
						}
					}
				}
			}
			for (Param p : currentModule.getParams()) {
				if (p.getName().equals(s)) {
					return p.getValue();
				}
			}
			return null;
		}
	};

	private SignalWidth convertToFixed(SignalWidth sw) {
		return convertToFixed(sw, paramsProvider, constParser);
	}
	
	public static SignalWidth convertToFixed(SignalWidth sw, ConstProvider paramsProvider, ConstProvider constParser) {
		if (sw.isFixed())
			return sw;

		SignalWidth fw = new SignalWidth(sw);
		for (SignalWidth ptr = fw; ptr != null; ptr = ptr.getNext()) {
			if (ptr.isText()) {
				ConstValue cv = ConstExprParser.parseExpr(ptr.getText(), paramsProvider, constParser, null);
				if (cv == null)
					Util.log.severe("Could not parse width " + ptr.getText());
				else
					ptr.set(cv.getBigInt().intValue());
			}
		}

		return fw;
	}

	private SignalWidth getModulePortWidth(Sig s, ParserRuleContext ctx) {
		SignalWidth aw = new SignalWidth();
		SignalWidth current = aw;
		SignalWidth w = s.getWidth();
		if (w != null)
			w = convertToFixed(w);
		while (w != null) {
			if (w.isText()) {
				ConstValue cv = ConstExprParser.parseExpr(w.getText(), paramsProvider, constParser, null);
				if (cv == null) {
					errorChecker.onTokenErrorFound(ctx, String.format(ErrorStrings.MODULE_DIM_PARSE_FALIED, s.getName()));
					current = current.getNext();
					break;
				} else if (!cv.isNumber()) {
					errorChecker.onTokenErrorFound(ctx, String.format(ErrorStrings.MODULE_IO_SIZE_NAN, s.getName()));
					current = current.getNext();
					break;
				} else if (cv.isNegative()) {
					errorChecker.onTokenErrorFound(ctx, String.format(ErrorStrings.ARRAY_SIZE_NEG, s.getName()));
					current = current.getNext();
					break;
				} else {
					int width = cv.getBigInt().intValue();
					if (current.isArray()) {
						current.getWidths().add(width);
					} else {
						current.setNext(new SignalWidth(width));
						current = current.getNext();
					}
				}
			} else if (w.isArray() && current.isArray()) {
				current.getWidths().addAll(w.getWidths());
			} else {
				current.setNext(new SignalWidth(w, false));
				current = current.getNext();
			}
			w = w.getNext();
		}

		if (aw.getWidths().isEmpty())
			if (aw.getNext() != null)
				aw = aw.getNext();
			else
				aw = new SignalWidth(1);

		return aw;
	}

	private void addSignalList(List<Sig> list, SignalWidth moduleArray, String instName, ParserRuleContext ctx) {
		for (Sig s : list) {
			checkSimpleArray(moduleArray);
			SignalWidth aw = new SignalWidth(getModulePortWidth(s, ctx));
			aw = convertToFixed(aw);
			SignalWidth width = null;
			if (moduleArray.getWidths().size() != 0) {
				width = new SignalWidth(moduleArray);
				if (aw.isArray()) {
					if (aw.getWidths().size() > 1 || aw.getWidths().get(0) > 1)
						width.getWidths().addAll(aw.getWidths());
					width.setNext(aw.getNext());
				} else {
					width.setNext(aw);
				}
			} else {
				width = aw;
			}

			widthMap.put(instName + "." + s.getName(), width);
		}
	}

	// this function checks the connections on a module to make sure the port
	// matches the signal
	private void checkConnectionWidths(String instName, ParserRuleContext ctx, ArrayList<Sig> ports) {
		for (AssignBlock block : errorChecker.getAssignBlock()) {
			if (block != null) {
				for (Connection c : block.connections) {
					if (!c.param) { // only check signals
						int idx = Util.findByName(ports, c.port);
						if (idx >= 0) {
							Sig s = ports.get(idx);
							if (c.connectionNode.sig_con() != null) {
								SignalWidth conWidth = widths.get(c.connectionNode.sig_con().expr());
								if (conWidth != null) {
									String sigName = instName + "." + c.port;
									SignalWidth portWidth = getModulePortWidth(s, ctx);

									if (!portWidth.equals(conWidth)) {
										if (block.instCon)
											errorChecker.onTokenErrorFound(c.signalNode, String.format(ErrorStrings.PORT_DIM_MISMATCH, c.signal, sigName));
										else
											errorChecker.onTokenErrorFound(ctx, String.format(ErrorStrings.PORT_DIM_MISMATCH, c.signal, sigName));
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public void module_inst(Module_instContext ctx, InstModule im) {
		if (ctx.name() != null && ctx.name().size() == 2) {
			String instName = ctx.name(1).getText();

			currentModule = im;
			if (currentModule == null)
				return;

			SignalWidth moduleArray = new SignalWidth();
			for (Array_sizeContext asc : ctx.array_size()) {
				if (widths.get(asc) != null)
					moduleArray.getWidths().add(widths.get(asc).getWidths().get(0));
			}

			im.setModuleWidth(moduleArray);
			im.setIsArray(ctx.array_size().size() != 0);

			Module type = currentModule.getType();

			addSignalList(type.getInputs(), moduleArray, instName, ctx);
			addSignalList(type.getOutputs(), moduleArray, instName, ctx);
			addSignalList(type.getInouts(), moduleArray, instName, ctx);

			if (moduleArray.getWidths().size() == 0)
				moduleArray.getWidths().add(1);

			// widthMap.put(instName, moduleArray);

			ArrayList<Sig> ports = new ArrayList<>();

			ports.addAll(type.getInputs());
			ports.addAll(type.getInouts());

			checkConnectionWidths(instName, ctx.name(1), ports);
		}
	}

	@Override
	public void exitDff_dec(Dff_decContext ctx) {
		for (Dff_singleContext dc : ctx.dff_single()) {
			if (dc.name() != null) {
				String instName = dc.name().getText();
				SignalWidth width = getArrayWidth(dc.array_size(), ctx.struct_type());

				widthMap.put(dc.name().getText() + ".d", width);
				widthMap.put(dc.name().getText() + ".q", width);

				widths.put(dc, width);

				ArrayList<Sig> ports = new ArrayList<>();
				ports.add(new Sig("rst", 1));
				ports.add(new Sig("clk", 1));

				checkConnectionWidths(instName, dc.name(), ports);
			}
		}
	}

	@Override
	public void exitFsm_dec(Fsm_decContext ctx) {
		if (ctx.name() != null) {
			String instName = ctx.name().getText();

			if (ctx.fsm_states() == null)
				return;

			int states = ctx.fsm_states().name().size();
			int w = Util.minWidthNum(states - 1);

			SignalWidth width;
			if (ctx.array_size().size() > 0)
				width = getArrayWidth(ctx.array_size(), null);
			else
				width = new SignalWidth();

			width.getWidths().add(w);

			widthMap.put(ctx.name().getText() + ".d", width);
			widthMap.put(ctx.name().getText() + ".q", width);

			widths.put(ctx, width);

			for (ParserRuleContext tn : ctx.fsm_states().name()) {
				widthMap.put(ctx.name().getText() + "." + tn.getText(), new SignalWidth(w));
			}

			ArrayList<Sig> ports = new ArrayList<>();
			ports.add(new Sig("rst", 1));
			ports.add(new Sig("clk", 1));

			checkConnectionWidths(instName, ctx.name(), ports);
		}
	}

	@Override
	public void exitNumber(NumberContext ctx) {
		widths.put(ctx, constExprParser.getValue(ctx).getArrayWidth());
	}

	private boolean getArrayWidth(SignalWidth width, SignalContext ctx, int idx) {
		return getArrayWidth(width, ctx, idx, ctx.children.size());
	}

	private boolean getArrayWidth(SignalWidth width, SignalContext ctx, int idx, int max) {
		return getArrayWidth(width, ctx, idx, max, this, this.boundsProvider, errorChecker);
	}

	/* Removes/edits "width" so that the index starting at "ctx" offset by "idx" are reflected. */
	static public boolean getArrayWidth(SignalWidth width, SignalContext ctx, int idx, int max, WidthProvider wp, BoundsProvider bp, TokenErrorListener listener) {
		for (int i = idx; i < max; i++) {
			ParseTree pt = ctx.children.get(i);

			if (pt instanceof Bit_selectionContext) {
				if (!getArrayWidth(width, (Bit_selectionContext) pt, wp, bp, listener))
					return false;

				// if we indexed to a single bit and there is more to the width
				if (width.getWidths().size() == 1 && width.getWidths().get(0) == 1 && width.getNext() != null)
					width.set(width.getNext()); // remove this index

			} else if (pt instanceof NameContext) {
				if (((NameContext) pt).CONST_ID() != null)
					continue;
				String name = pt.getText();

				if (!width.isStruct() && width.getWidths().size() == 1 && width.getWidths().get(0) == 1 && width.getNext() != null)
					width.set(width.getNext());
				if (!width.isStruct()) {
					StringBuilder sb = new StringBuilder();
					for (int j = 0; j < i - 1; j++)
						sb.append(ctx.children.get(j).getText());
					if (listener != null)
						listener.onTokenErrorFound((NameContext) pt, String.format(ErrorStrings.NOT_A_MEMBER, pt.getText(), sb.toString()));
					return false;
				}
				SignalWidth w = width.getStruct().getWidthOfMember(name);
				if (w == null) {
					if (listener != null)
						listener.onTokenErrorFound((NameContext) pt, String.format(ErrorStrings.UNKNOWN_STRUCT_NAME, name, width.getStruct()));
				} else {
					width.set(w);
				}

			} else if (pt instanceof TerminalNode) {
				continue;
			} else {
				System.out.println("Uknown " + ctx.getText());
			}
		}

		return true;
	}

	static private boolean getArrayWidth(SignalWidth width, Bit_selectionContext ctx, WidthProvider wp, BoundsProvider bp, TokenErrorListener listener) {

		if (width != null) {
			if (ctx == null) {
				return true;
			} else {
				if (ctx.getChildCount() <= width.getDepth()) {
					if (width.isStruct()) {
						if (listener != null)
							listener.onTokenErrorFound(ctx, ErrorStrings.STRUCT_NOT_ARRAY);
						return false;
					}
					for (Array_indexContext aic : ctx.array_index()) {
						ArrayBounds b = bp.getBounds(aic);
						if (b != null && !b.fitInWidth(width.getWidths().get(0))) {
							if (listener != null)
								listener.onTokenErrorFound(aic, ErrorStrings.ARRAY_INDEX_OUT_OF_BOUNDS);
						}
						width.getWidths().remove(0);
					}
					if (ctx.bit_selector() != null) {
						ArrayBounds b = bp.getBounds(ctx.bit_selector());

						if (b != null && !b.fitInWidth(width.getWidths().get(0))) {
							if (listener != null)
								listener.onTokenErrorFound(ctx.bit_selector(), ErrorStrings.ARRAY_INDEX_OUT_OF_BOUNDS);
						}
						if (wp.getWidth(ctx.bit_selector()) != null)
							width.getWidths().set(0, wp.getWidth(ctx.bit_selector()).getWidths().get(0));
					}
					if (width.getWidths().size() == 0)
						width.getWidths().add(1);

					return true;
				} else {
					if (ctx.array_index().size() > width.getDepth()) {
						if (listener != null)
							listener.onTokenErrorFound(ctx.array_index(width.getDepth()), ErrorStrings.ARRAY_INDEX_DIM_MISMATCH);
					} else if (ctx.bit_selector() != null) {
						if (listener != null)
							listener.onTokenErrorFound(ctx.bit_selector(), ErrorStrings.ARRAY_INDEX_DIM_MISMATCH);
					}
				}
			}
		}
		System.out.println("Width was null");
		return false;
	}

	private boolean getFsmArrayWidth(SignalWidth width, Bit_selectionContext ctx) {
		checkSimpleArray(width);
		if (width != null) {
			if (ctx == null) {
				return true;
			} else {
				if (ctx.getChildCount() < width.getWidths().size()) {
					for (Array_indexContext aic : ctx.array_index()) {
						ArrayBounds b = boundsProvider.getBounds(aic);
						if (b != null && !b.fitInWidth(width.getWidths().get(0))) {
							errorChecker.onTokenErrorFound(aic, ErrorStrings.ARRAY_INDEX_OUT_OF_BOUNDS);
						}
						width.getWidths().remove(0);
					}
					if (ctx.bit_selector() != null) {
						ArrayBounds b = boundsProvider.getBounds(ctx.bit_selector());

						if (b != null && !b.fitInWidth(width.getWidths().get(0))) {
							errorChecker.onTokenErrorFound(ctx.bit_selector(), ErrorStrings.ARRAY_INDEX_OUT_OF_BOUNDS);
						}
						if (widths.get(ctx.bit_selector()) != null)
							width.getWidths().set(0, widths.get(ctx.bit_selector()).getWidths().get(0));
					}

					return true;
				} else {
					if (ctx.array_index().size() >= width.getWidths().size())
						errorChecker.onTokenErrorFound(ctx.array_index(width.getWidths().size() - 1), ErrorStrings.ARRAY_INDEX_DIM_MISMATCH);
					else if (ctx.bit_selector() != null)
						errorChecker.onTokenErrorFound(ctx.bit_selector(), ErrorStrings.ARRAY_INDEX_DIM_MISMATCH);
				}
			}
		}
		return false;
	}

	@Override
	public void exitArray_size(Array_sizeContext ctx) {
		if (ctx.expr() != null) {
			ConstValue cv = constExprParser.getValue(ctx.expr());

			if (cv == null) {
				debugNullConstant(ctx.expr());
				if (!constExprParser.isConstant(ctx.expr()))
					errorChecker.onTokenErrorFound(ctx.expr(), String.format(ErrorStrings.EXPR_NOT_CONSTANT, ctx.expr().getText()));
				return;
			}

			if (cv.isArray()) {
				errorChecker.onTokenErrorFound(ctx.expr(), ErrorStrings.ARRAY_SIZE_MULTI_DIM);
				return;
			}

			if (!cv.isNumber()) {
				errorChecker.onTokenErrorFound(ctx.expr(), ErrorStrings.ARRAY_SIZE_NAN);
				return;
			}
			if (cv.isNegative()) {
				errorChecker.onTokenErrorFound(ctx.expr(), ErrorStrings.ARRAY_SIZE_NEG);
				return;
			}
			try {
				widths.put(ctx, new SignalWidth(cv.getBigInt().intValue()));
			} catch (ArithmeticException e) {
				errorChecker.onTokenWarningFound(ctx.expr(), ErrorStrings.ARRAY_SIZE_TOO_BIG);
			}
		}
	}

	@Override
	public void exitBitSelectorConst(BitSelectorConstContext ctx) {
		ArrayBounds b = boundsProvider.getBounds(ctx);
		if (b != null)
			widths.put(ctx, new SignalWidth(b.getWidth()));
	}

	@Override
	public void exitBitSelectorFixWidth(BitSelectorFixWidthContext ctx) {
		if (ctx.expr().size() != 2)
			return;

		ConstValue width = constExprParser.getValue(ctx.expr(1));
		if (width != null && width.isNumber())
			widths.put(ctx, new SignalWidth(width.getBigInt().intValue()));

	}

	@Override
	public SignalWidth getWidth(ParserRuleContext ctx) {
		return widths.get(ctx);
	}

	@Override
	public SignalWidth getWidth(String signal) {
		SignalWidth w = widthMap.get(signal);
		if (w == null) {
			if (Util.containsName(errorChecker.getInouts(), signal))
				signal = signal + ".enable";
			else if (Util.containsName(errorChecker.getDffs(), signal))
				signal = signal + ".d";
			else if (Util.containsName(errorChecker.getFsms(), signal))
				signal = signal + ".d";
			else if (Util.containsName(errorChecker.getInstModules(), signal)) {
				InstModule im = Util.getByName(errorChecker.getInstModules(), signal);
				if (im != null) {
					return im.getModuleWidth();
				}
			}
			w = widthMap.get(signal);
		}

		return w;
	}

	@Override
	public SignalWidth checkWidthMap(String signal) {
		return widthMap.get(signal);
	}

	public static String getName(SignalContext ctx, WidthProvider wp) {
		boolean isWidth = ctx.name(ctx.name().size() - 1).getText().equals(Lucid.WIDTH_ATTR);
		StringBuilder sb = new StringBuilder();
		sb.append(ctx.name(0).getText());
		int size = ctx.name().size();
		if (isWidth)
			size--; // exclude WIDTH name
		int i = 0;
		if (wp.checkWidthMap(sb.toString()) == null)
			for (i = 1; i < size; i++) {
				sb.append(".").append(ctx.name(i).getText());
				if (wp.checkWidthMap(sb.toString()) != null)
					return sb.toString();
			}
		if (wp.getWidth(ctx.name(0).getText()) != null)
			return ctx.name(0).getText();
		return null;
	}

	@Override
	public void exitSignal(SignalContext ctx) {

		if (ctx.name().size() > 0) {
			if (ctx.name(0).SPACE_ID() != null) {
				ConstValue cv = constExprParser.getValue(ctx);
				if (cv != null)
					widths.put(ctx, cv.getArrayWidth());
				debug(ctx);
				return;
			}

			boolean isWidth = ctx.name(ctx.name().size() - 1).getText().equals(Lucid.WIDTH_ATTR);

			StringBuilder sb = new StringBuilder();
			sb.append(ctx.name(0).getText());
			int size = ctx.name().size();
			if (isWidth)
				size--; // exclude WIDTH name
			int i = 0;

			SignalWidth aw = null;

			String sigName = getName(ctx, this);
			if (sigName != null) {
				i = sigName.split("\\.").length - 1;
				aw = getWidth(sigName);
			}

			if (i < size && ctx.bit_selection().size() > 0 && ctx.children.indexOf(ctx.name(i)) > ctx.children.indexOf(ctx.bit_selection(0))) {
				errorChecker.onTokenErrorFound(ctx.bit_selection(0), ErrorStrings.BIT_SELECTOR_IN_NAME);
				return;
			}

			if (i == size)
				i--;
			i = ctx.children.indexOf(ctx.name(i)) + 1;

			if (aw != null && aw.getDepth() > 0) {

				if (isWidth) { // need to find the width of width
					aw = new SignalWidth(aw);
					int max = ctx.children.size() - 1;
					// if the last child is a bit selector
					if (ctx.children.get(ctx.children.size() - 1) instanceof Bit_selectionContext)
						max--;
					// get the width of the signal before .WIDTH
					getArrayWidth(aw, ctx, i, max);
					if (aw.isSimpleArray()) {
						if (aw.getDepth() > 1) { // multi dimensional
							max = Integer.MIN_VALUE;
							for (int j : aw.getDimensions())
								max = Math.max(max, j); // find largest value

							// each element in the array must be the same size
							// so we use the smallest size that fits the largest element
							int w = Util.minWidthNum(max);

							SignalWidth width = new SignalWidth();
							width.getWidths().add(aw.getDepth());
							width.getWidths().add(w);
							aw = width;
						} else {
							// only one dimension so just add the single width width
							aw = new SignalWidth(Util.minWidthNum(aw.getWidths().get(0)));
						}
						if (ctx.children.get(ctx.children.size() - 1) instanceof Bit_selectionContext) {
							aw = new SignalWidth(aw);
							if (getArrayWidth(aw, ctx, ctx.children.size() - 2))
								widths.put(ctx, aw);
						} else {
							widths.put(ctx, aw);
						}
					}
				} else {
					aw = new SignalWidth(aw);
					if (errorChecker.isFSM(ctx.name(0).getText())) {
						if (ctx.bit_selection().size() == 1) {
							if (getFsmArrayWidth(aw, ctx.bit_selection(0)))
								widths.put(ctx, aw);
						} else if (ctx.bit_selection().size() > 1) {
							errorChecker.onTokenErrorFound(ctx.bit_selection(1), ErrorStrings.EXTRA_BIT_SELECTORS);
						} else {
							widths.put(ctx, aw);
						}
					} else {
						if (getArrayWidth(aw, ctx, i))
							widths.put(ctx, aw);
					}
				}

			}
		}
		debug(ctx);
	}

	@Override
	public void exitAssign_stat(Assign_statContext ctx) {
		if (ctx.signal() != null && ctx.expr() != null) {
			SignalWidth sigWidth = widths.get(ctx.signal());
			SignalWidth exprWidth = widths.get(ctx.expr());

			if (sigWidth != null && exprWidth != null) {
				if (sigWidth.getDepth() == 0) {
					System.out.println("Dimensions for signal " + ctx.signal().getText() + " is zero!");
					return;
				}
				if (exprWidth.getDepth() == 0) {
					System.out.println("Dimensions for signal " + ctx.expr().getText() + " is zero!");
					return;
				}
				if (sigWidth.getDepth() > 1 || sigWidth.isStruct()) {
					if (!sigWidth.equals(exprWidth))
						errorChecker.onTokenErrorFound(ctx.expr(), ErrorStrings.ASSIGN_ARRAY_DIM_MISMATCH);
				} else if (exprWidth.getDepth() > 1) {
					errorChecker.onTokenErrorFound(ctx.expr(), ErrorStrings.ASSIGN_SIG_NOT_ARRAY);
				} else if (sigWidth.isSimpleArray() && exprWidth.isSimpleArray() && ctx.expr().getClass() == ExprSignalContext.class
						&& (sigWidth.getWidths().get(0) < exprWidth.getWidths().get(0))) {
					errorChecker.onTokenWarningFound(ctx.expr(), String.format(ErrorStrings.TRUNC_WARN, ctx.expr().getText(), ctx.signal().getText()));
				}
			}
		}
	}

	/*************** expr ********************/

	@Override
	public void exitExprSignal(ExprSignalContext ctx) {
		widths.put(ctx, widths.get(ctx.signal()));
		debug(ctx);
	}

	@Override
	public void exitExprNum(ExprNumContext ctx) {
		if (constExprParser.getValue(ctx) != null)
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
		debug(ctx);
	}

	@Override
	public void exitExprFunction(ExprFunctionContext ctx) {
		widths.put(ctx, widths.get(ctx.function()));
		debug(ctx);
	}

	@Override
	public void exitExprGroup(ExprGroupContext ctx) {
		widths.put(ctx, widths.get(ctx.expr()));
		debug(ctx);
	}

	@Override
	public void exitExprConcat(ExprConcatContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.expr().size() > 0) {
			for (ExprContext ec : ctx.expr())
				if (widths.get(ec) == null)
					return;

			SignalWidth base = widths.get(ctx.expr(0));

			if (!base.isSimpleArray()) {
				errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ARRAY_CONCAT_STRUCT);
				return;
			}

			ArrayList<Integer> baseWidths = new ArrayList<Integer>(base.getWidths());
			baseWidths.remove(0);
			ArrayList<Integer> tempList = new ArrayList<Integer>(baseWidths.size());

			for (int i = 1; i < ctx.expr().size(); i++) {
				SignalWidth w = widths.get(ctx.expr(i));
				if (!w.isSimpleArray()) {
					errorChecker.onTokenErrorFound(ctx.expr(i), ErrorStrings.ARRAY_CONCAT_STRUCT);
					return;
				}
				if (w == null || w.getWidths().size() == 0)
					continue;
				tempList.clear();
				tempList.addAll(w.getWidths());
				tempList.remove(0);
				if (!baseWidths.equals(tempList)) { // match all but first dim
					errorChecker.onTokenErrorFound(ctx.expr(i), ErrorStrings.ARRAY_CONCAT_DIM_MISMATCH);
					return;
				}
			}

			SignalWidth w = widths.get(ctx.expr(0));
			SignalWidth aw = new SignalWidth(w);
			checkSimpleArray(aw);

			int wSum = 0;
			for (int i = 0; i < ctx.expr().size(); i++) {
				SignalWidth wi = widths.get(ctx.expr(i));
				checkSimpleArray(wi);
				if (wi == null)
					return;
				wSum += wi.getWidths().get(0);
			}
			aw.getWidths().set(0, wSum);

			widths.put(ctx, aw);
		}
		debug(ctx);
	}

	@Override
	public void exitExprDup(ExprDupContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		ConstValue dupValue = constExprParser.getValue(ctx.expr(0));

		if (dupValue == null)
			return;

		if (dupValue.isArray()) {
			errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ARRAY_DUP_INDEX_MULTI_DIM);
			return;
		}

		if (!dupValue.isNumber()) {
			errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ARRAY_DUP_INDEX_NAN);
			return;
		}

		if (widths.get(ctx.expr(1)) == null)
			return;

		SignalWidth aw = new SignalWidth(widths.get(ctx.expr(1)));
		if (!aw.isSimpleArray()) {
			errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ARRAY_DUP_STRUCT);
			return;
		}

		aw.getWidths().set(0, (int) (aw.getWidths().get(0) * dupValue.getBigInt().intValue()));
		widths.put(ctx, aw);
		debug(ctx);
	}

	@Override
	public void exitExprArray(ExprArrayContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.expr().size() > 0) {
			for (ExprContext ec : ctx.expr())
				if (widths.get(ec) == null)
					return;

			SignalWidth baseDim = widths.get(ctx.expr(0));
			boolean error = false;
			for (ExprContext ec : ctx.expr())
				if (!widths.get(ec).equals(baseDim)) {
					errorChecker.onTokenErrorFound(ec, ErrorStrings.ARRAY_BUILDING_DIM_MISMATCH);
					error = true;
				}
			if (error)
				return;

			SignalWidth aw = new SignalWidth(widths.get(ctx.expr(0)));
			if (aw.isArray()) {
				aw.getWidths().add(0, ctx.expr().size());
			} else {
				SignalWidth nw = new SignalWidth(ctx.expr().size());
				nw.setNext(aw);
				aw = nw;
			}
			widths.put(ctx, aw);
		}
		debug(ctx);
	}

	@Override
	public void exitExprNegate(ExprNegateContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		widths.put(ctx, widths.get(ctx.expr()));
		debug(ctx);
	}

	@Override
	public void exitExprInvert(ExprInvertContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.getChildCount() == 2)
			if (ctx.getChild(0).getText().equals("~")) {
				widths.put(ctx, widths.get(ctx.expr()));
			} else { // ! operator
				widths.put(ctx, new SignalWidth(1));
			}
		debug(ctx);
	}

	@Override
	public void exitExprMultDiv(ExprMultDivContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.getChildCount() == 3) {
			SignalWidth op1 = widths.get(ctx.expr(0));
			SignalWidth op2 = widths.get(ctx.expr(1));

			if (op1 == null || op2 == null)
				return;

			if (!op1.isSimpleArray())
				errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.MUL_DIV_STRUCT);

			if (!op2.isSimpleArray())
				errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.MUL_DIV_STRUCT);

			if (!op1.isSimpleArray() || !op2.isSimpleArray())
				return;

			if (ctx.getChild(1).getText().equals("*")) { // multiply
				if (op1.getWidths().size() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.MUL_MULTI_DIM);
				if (op2.getWidths().size() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.MUL_MULTI_DIM);
				if (op1.getWidths().size() != 1 || op2.getWidths().size() != 1)
					return;

				widths.put(ctx, new SignalWidth(Util.widthOfMult(op1.getWidths().get(0), op2.getWidths().get(0))));
			} else { // divide
				if (op1.getWidths().size() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.DIV_MULTI_DIM);
				if (op2.getWidths().size() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.DIV_MULTI_DIM);
				if (op1.getWidths().size() != 1 || op2.getWidths().size() != 1)
					return;

				widths.put(ctx, new SignalWidth(op1.getWidths().get(0))); // width of division is at most the width of the first arg
			}
		}
		debug(ctx);
	}

	@Override
	public void exitExprAddSub(ExprAddSubContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.getChildCount() == 3) {
			SignalWidth op1 = widths.get(ctx.expr(0));
			SignalWidth op2 = widths.get(ctx.expr(1));

			if (op1 == null || op2 == null)
				return;

			if (!op1.isSimpleArray())
				errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ADD_SUB_NOT_ARRAY);

			if (!op2.isSimpleArray())
				errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.ADD_SUB_NOT_ARRAY);

			if (!op1.isSimpleArray() || !op2.isSimpleArray())
				return;

			if (ctx.getChild(1).getText().equals("+")) {
				if (op1.getDepth() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.ADD_MULTI_DIM);
				if (op2.getDepth() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.ADD_MULTI_DIM);
			} else { // subtact
				if (op1.getDepth() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.SUB_MULTI_DIM);
				if (op2.getDepth() != 1)
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.SUB_MULTI_DIM);
			}

			if (op1.getDepth() != 1 || op2.getDepth() != 1)
				return;

			widths.put(ctx, new SignalWidth(Math.max(op1.getWidths().get(0), op2.getWidths().get(0)) + 1));
		}
		debug(ctx);
	}

	@Override
	public void exitExprShift(ExprShiftContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.getChildCount() == 3) {
			SignalWidth op1 = widths.get(ctx.expr(0));
			ConstValue op2 = constExprParser.getValue(ctx.expr(1));

			if (op1 == null || op2 == null)
				return;

			if (!op1.isSimpleArray()) {
				errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.SHIFT_NOT_ARRAY);
				return;
			}

			String operand = ctx.getChild(1).getText();

			if (op1.getDepth() != 1) {
				return;
			}
			if (op2.isArray()) {
				return;
			}

			if (!op2.isNumber())
				widths.put(ctx, new SignalWidth(op1));
			else {
				switch (operand) {
				case ">>":
				case ">>>":
					widths.put(ctx, new SignalWidth(op1));
					break;
				case "<<":
				case "<<<":
					widths.put(ctx, new SignalWidth(op1.getWidths().get(0) + op2.getBigInt().intValue()));
					break;
				default:
					Util.log.severe("BUG: Unknown shift operator!");
					break;
				}
			}
		}
		debug(ctx);
	}

	@Override
	public void exitExprAndOr(ExprAndOrContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		if (ctx.getChildCount() == 3) {
			boolean andOp = ctx.getChild(1).getText().equals("&");
			SignalWidth op1 = widths.get(ctx.expr(0));
			SignalWidth op2 = widths.get(ctx.expr(1));

			if (op1 == null || op2 == null)
				return;

			if ((op1.getDepth() != 1 || op2.getDepth() != 1 || !op1.isSimpleArray() || !op2.isSimpleArray()) && !op1.equals(op2)) {
				if (andOp)
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.AND_MULTI_DIM_MISMATCH);
				else
					errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OR_MULTI_DIM_MISMATCH);
				return;
			}

			if (op1.isSimpleArray() && op1.getDepth() == 1) {
				widths.put(ctx, new SignalWidth(Math.max(op1.getWidths().get(0), op2.getWidths().get(0))));
			} else
				widths.put(ctx, op1); // arrays don't change dimensions
		}
		debug(ctx);
	}

	@Override
	public void exitExprCompress(ExprCompressContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		widths.put(ctx, new SignalWidth(1));
		debug(ctx);
	}

	@Override
	public void exitExprCompare(ExprCompareContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		widths.put(ctx, new SignalWidth(1));

		if (ctx.getChildCount() == 3) {
			String operand = ctx.getChild(1).getText();
			boolean equality = operand.equals("==") || operand.equals("!=");

			SignalWidth w1 = widths.get(ctx.expr(0));
			SignalWidth w2 = widths.get(ctx.expr(1));

			if (w1 == null || w2 == null)
				return;

			if (equality) {
				if ((w1.getDepth() != 1 || w2.getDepth() != 1 || !w1.isSimpleArray() || !w2.isSimpleArray()) && !w1.equals(w2)) {
					if (operand.equals("=="))
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_EQ_DIM_MISMATCH);
					else
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_NEQ_DIM_MISMATCH);
				}
			} else {
				if (w1.getDepth() != 1 || !w1.isSimpleArray())
					switch (operand) {
					case "<":
						errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.OP_LT_ARRAY);
						break;
					case ">":
						errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.OP_GT_ARRAY);
						break;
					case "<=":
						errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.OP_LTE_ARRAY);
						break;
					case ">=":
						errorChecker.onTokenErrorFound(ctx.expr(0), ErrorStrings.OP_GTE_ARRAY);
						break;
					}
				if (w2.getDepth() != 1 || !w2.isSimpleArray())
					switch (operand) {
					case "<":
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_LT_ARRAY);
						break;
					case ">":
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_GT_ARRAY);
						break;
					case "<=":
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_LTE_ARRAY);
						break;
					case ">=":
						errorChecker.onTokenErrorFound(ctx.expr(1), ErrorStrings.OP_GTE_ARRAY);
						break;
					}
			}

		}
		debug(ctx);

	}

	@Override
	public void exitExprLogical(ExprLogicalContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}

		widths.put(ctx, new SignalWidth(1));
		debug(ctx);
	}

	@Override
	public void exitExprTernary(ExprTernaryContext ctx) {
		if (constExprParser.getValue(ctx) != null) {
			widths.put(ctx, new SignalWidth(constExprParser.getValue(ctx).getWidths()));
			return;
		}
		if (ctx.expr().size() == 3) {
			SignalWidth w1 = widths.get(ctx.expr(1));
			SignalWidth w2 = widths.get(ctx.expr(2));

			if (w1 == null || w2 == null)
				return;

			if (w1.getDepth() != 1 || w2.getDepth() != 1 || !w1.isSimpleArray() || !w2.isSimpleArray()) {
				if (!w1.equals(w2))
					errorChecker.onTokenErrorFound(ctx.expr(2), ErrorStrings.OP_TERN_DIM_MISMATCH);
				widths.put(ctx, w1);
			} else {
				if (w1.getWidths().get(0) >= w2.getWidths().get(0))
					widths.put(ctx, w1);
				else
					widths.put(ctx, w2);
			}
		}
		debug(ctx);
	}

	/************* end expr ******************/
}