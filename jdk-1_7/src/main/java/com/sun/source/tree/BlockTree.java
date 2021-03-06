/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.source.tree;

import java.util.List;

/**
 * A tree node for a statement block.
 * <p>
 * For example:
 * <pre>
 *   { }
 *
 *   { <em>statements</em> }
 *
 *   static { <em>statements</em> }
 * </pre>
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 * @jls section 14.2
 * @since 1.6
 */
public interface BlockTree extends StatementTree {
    boolean isStatic();

    List<? extends StatementTree> getStatements();
}
