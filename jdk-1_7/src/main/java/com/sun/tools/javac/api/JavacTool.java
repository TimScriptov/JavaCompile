/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.api;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavacOption;
import com.sun.tools.javac.main.JavacOption.OptionKind;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.RecognizedOptions;
import com.sun.tools.javac.main.RecognizedOptions.GrumpyHelper;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Pair;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * TODO: describe com.sun.tools.javac.api.Tool
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah\u00e9
 */
public final class JavacTool implements JavaCompiler {
    private final List<Pair<String, String>> options
            = new ArrayList<Pair<String, String>>();
    private final Context dummyContext = new Context();

    private final PrintWriter silent = new PrintWriter(new OutputStream() {
        public void write(int b) {
        }
    });

    private final Main sharedCompiler = new Main("javac", silent);

    {
        sharedCompiler.setOptions(Options.instance(dummyContext));
    }

    /**
     * Constructor used by service provider mechanism.  The correct way to
     * obtain an instance of this class is using create or the service provider
     * mechanism.
     *
     * @see javax.tools.JavaCompilerTool
     * @see ToolProvider
     * @see #create
     */
    @Deprecated
    public JavacTool() {
    }

    /**
     * Static factory method for creating new instances of this tool.
     *
     * @return new instance of this tool
     */
    public static JavacTool create() {
        return new JavacTool();
    }

    private String argsToString(Object... args) {
        String newArgs = null;
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            for (Object arg : args) {
                sb.append(separator).append(arg.toString());
                separator = File.pathSeparator;
            }
            newArgs = sb.toString();
        }
        return newArgs;
    }

    private void setOption1(String name, OptionKind kind, Object... args) {
        String arg = argsToString(args);
        JavacOption option = sharedCompiler.getOption(name);
        if (option == null || !match(kind, option.getKind()))
            throw new IllegalArgumentException(name);
        if ((args.length != 0) != option.hasArg())
            throw new IllegalArgumentException(name);
        if (option.hasArg()) {
            if (option.process(null, name, arg)) // FIXME
                throw new IllegalArgumentException(name);
        } else {
            if (option.process(null, name)) // FIXME
                throw new IllegalArgumentException(name);
        }
        options.add(new Pair<String, String>(name, arg));
    }

    public void setOption(String name, Object... args) {
        setOption1(name, OptionKind.NORMAL, args);
    }

    public void setExtendedOption(String name, Object... args) {
        setOption1(name, OptionKind.EXTENDED, args);
    }

    private static boolean match(OptionKind clientKind, OptionKind optionKind) {
        return (clientKind == (optionKind == OptionKind.HIDDEN ? OptionKind.EXTENDED : optionKind));
    }

    public JavacFileManager getStandardFileManager(
            DiagnosticListener<? super JavaFileObject> diagnosticListener,
            Locale locale,
            Charset charset) {
        Context context = new Context();
        context.put(Locale.class, locale);
        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);
        PrintWriter pw = (charset == null)
                ? new PrintWriter(System.err, true)
                : new PrintWriter(new OutputStreamWriter(System.err, charset), true);
        context.put(Log.outKey, pw);
        return new JavacFileManager(context, true, charset);
    }

    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits) {
        try {
            Context context = new Context();
            ClientCodeWrapper ccw = ClientCodeWrapper.instance(context);

            final String kindMsg = "All compilation units must be of SOURCE kind";
            if (options != null)
                for (String option : options)
                    option.getClass(); // null check
            if (classes != null) {
                for (String cls : classes)
                    if (!SourceVersion.isName(cls)) // implicit null check
                        throw new IllegalArgumentException("Not a valid class name: " + cls);
            }
            if (compilationUnits != null) {
                compilationUnits = ccw.wrapJavaFileObjects(compilationUnits); // implicit null check
                for (JavaFileObject cu : compilationUnits) {
                    if (cu.getKind() != JavaFileObject.Kind.SOURCE)
                        throw new IllegalArgumentException(kindMsg);
                }
            }

            if (diagnosticListener != null)
                context.put(DiagnosticListener.class, ccw.wrap(diagnosticListener));

            if (out == null)
                context.put(Log.outKey, new PrintWriter(System.err, true));
            else
                context.put(Log.outKey, new PrintWriter(out, true));

            if (fileManager == null)
                fileManager = getStandardFileManager(diagnosticListener, null, null);
            fileManager = ccw.wrap(fileManager);
            context.put(JavaFileManager.class, fileManager);
            processOptions(context, fileManager, options);
            Main compiler = new Main("javacTask", context.get(Log.outKey));
            return new JavacTaskImpl(compiler, options, context, classes, compilationUnits);
        } catch (ClientCodeException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    private static void processOptions(Context context,
                                       JavaFileManager fileManager,
                                       Iterable<String> options) {
        if (options == null)
            return;

        Options optionTable = Options.instance(context);

        JavacOption[] recognizedOptions =
                RecognizedOptions.getJavacToolOptions(new GrumpyHelper());
        Iterator<String> flags = options.iterator();
        while (flags.hasNext()) {
            String flag = flags.next();
            int j;
            for (j = 0; j < recognizedOptions.length; j++)
                if (recognizedOptions[j].matches(flag))
                    break;

            if (j == recognizedOptions.length) {
                if (fileManager.handleOption(flag, flags)) {
                    continue;
                } else {
                    String msg = Main.getLocalizedString("err.invalid.flag", flag);
                    throw new IllegalArgumentException(msg);
                }
            }

            JavacOption option = recognizedOptions[j];
            if (option.hasArg()) {
                if (!flags.hasNext()) {
                    String msg = Main.getLocalizedString("err.req.arg", flag);
                    throw new IllegalArgumentException(msg);
                }
                String operand = flags.next();
                if (option.process(optionTable, flag, operand))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag + " " + operand);
            } else {
                if (option.process(optionTable, flag))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag);
            }
        }
    }

    public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
        if (err == null)
            err = System.err;
        for (String argument : arguments)
            argument.getClass(); // null check
        return com.sun.tools.javac.Main.compile(arguments, new PrintWriter(err, true));
    }

    public Set<SourceVersion> getSourceVersions() {
        return Collections.unmodifiableSet(EnumSet.range(SourceVersion.RELEASE_3,
                SourceVersion.latest()));
    }

    public int isSupportedOption(String option) {
        JavacOption[] recognizedOptions =
                RecognizedOptions.getJavacToolOptions(new GrumpyHelper());
        for (JavacOption o : recognizedOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
    }

}
