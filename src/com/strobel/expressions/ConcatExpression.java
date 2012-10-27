package com.strobel.expressions;

import com.strobel.collections.ImmutableList;
import com.strobel.collections.ListBuffer;
import com.strobel.reflection.Type;
import com.strobel.reflection.Types;

/**
 * @author Mike Strobel
 */
public final class ConcatExpression extends Expression {
    private final ExpressionList<? extends Expression> _operands;

    public ConcatExpression(final ExpressionList<? extends Expression> operands) {
        _operands = operands;
    }

    public ExpressionList<? extends Expression> getOperands() {
        return _operands;
    }

    @Override
    public ExpressionType getNodeType() {
        return ExpressionType.Extension;
    }

    @Override
    public Type<?> getType() {
        return Types.String;
    }

    @Override
    protected Expression accept(final ExpressionVisitor visitor) {
        return visitor.visitConcat(this);
    }

    public final ConcatExpression update(final ExpressionList<? extends Expression> operands) {
        if (operands == _operands) {
            return this;
        }
        return concat(operands);
    }

    final ConcatExpression rewrite(final ExpressionList<? extends Expression> operands) {
        assert operands == null || operands.size() == _operands.size();
        return concat(operands);
    }

    @Override
    protected Expression visitChildren(final ExpressionVisitor visitor) {
        return update(visitor.visit(getOperands()));
    }

    @Override
    public boolean canReduce() {
        return true;
    }

    @Override
    public Expression reduce() {
        final ExpressionList<? extends Expression> originalOperands = _operands;
        final ListBuffer<Expression> reducedOperands = ListBuffer.lb();

        for (int i = 0, n = originalOperands.size(); i < n; i++) {
            final Expression operand = originalOperands.get(i);
            
            if (!ConstantCheck.isStringLiteral(operand)) {
                reducedOperands.add(operand);
                continue;
            }
            
            StringBuilder sb = null; 
            
            for (int j = i + 1; j < n; j++) {
                final Expression nextOperand = originalOperands.get(j);

                if (!ConstantCheck.isStringLiteral(nextOperand)) {
                    break;
                }
                
                if (sb == null) {
                    sb = new StringBuilder();
                    sb.append((String)((ConstantExpression)operand).getValue());
                }
            
                sb.append((String)((ConstantExpression)nextOperand).getValue());
                ++i;
            }

            reducedOperands.add(
                sb != null ? constant(sb.toString())
                           : operand
            );
        }

        final ParameterExpression sb = variable(Types.StringBuilder);
        final Expression[] body = new Expression[reducedOperands.size() + 2];

        body[0] = assign(
            sb,
            makeNew(Types.StringBuilder.getConstructor())
        );

        int i = 0;

        for (ImmutableList<Expression> o = reducedOperands.toList();
             o.nonEmpty();
             o = o.tail) {

            body[++i] = call(sb, "append", o.head);
        }

        body[body.length - 1] = call(sb, "toString");

        return block(
            new ParameterExpression[] { sb },
            body
        );
    }
}