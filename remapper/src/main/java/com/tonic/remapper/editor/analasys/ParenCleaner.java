package com.tonic.remapper.editor.analasys;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParenCleaner {

    private static final Pattern CAST_THIS  =
            Pattern.compile("\\(\\(\\s*([\\w.$]+)\\s*\\)\\s*\\(\\s*this\\s*\\)\\)");
    private static final Pattern CAST_IDENT =
            Pattern.compile("\\(\\(\\s*([\\w.$]+)\\s*\\)\\s*\\(\\s*([\\w.$]+)\\s*\\)\\)");
    private static final Pattern DOUBLE_NUM =
            Pattern.compile("\\(\\(\\s*([+-]?\\d+)\\s*\\)\\)");

    private ParenCleaner() {}

    public static String clean(String code) {
        code = replaceAll(code, CAST_THIS,  "(($1)this)");
        code = replaceAll(code, CAST_IDENT, "(($1)$2)");
        code = replaceAll(code, DOUBLE_NUM, "($1)");
        return code;
    }

    private static String replaceAll(String in, Pattern p, String repl) {
        Matcher m = p.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, repl);
        m.appendTail(sb);
        return sb.toString();
    }
}
