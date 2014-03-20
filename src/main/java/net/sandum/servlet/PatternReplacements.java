
package net.sandum.servlet;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author      osa
 * @since       24-02-2014
 * @version     $Id: PatternReplacements.java -1 24-02-2014 22:42:53 osa $
 */
public class PatternReplacements {

    private static class PR {
        private final Pattern pattern;
        private final String replacement;

        public PR(String regex, String replacement) {
            if (regex == null)
                throw new IllegalArgumentException("null pattern not allowed");
            if (replacement == null)
                throw new IllegalArgumentException("null replacement not allowed");
            this.pattern = Pattern.compile(regex);
            this.replacement = replacement;
        }

        public String match(String src) {
            Matcher m = pattern.matcher(src);
            return m.matches() ?  m.replaceFirst(replacement) : null;
        }

        @Override
        public int hashCode() {
            return (7 + pattern.hashCode()) * 83 + replacement.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;
            if (getClass() != o.getClass())
                return false;

            final PR that = (PR)o;
            return this.pattern.equals(that.pattern) && this.replacement.equals(that.replacement);
        }
    };

    private final List<PR> prs = new LinkedList<PR>();

    public boolean addPatternReplacement(String p, String r) {
        return prs.add(new PR(p, r));
    }

    public String match(String src) {
        for (PR pr : prs) {
            String res = pr.match(src);
            if (res != null)
                return res;
        }
        return null;
    }
}
