// Adapted from SysAdminDoc/ClearCut, MIT; see third_party/clearcut/LICENSE.
package com.monstro.v18.clearcut

object DiagnosticRedaction {
        private val SENSITIVE_PATTERNS = listOf(
            Regex("""content://[^\s)"']+"""),
            Regex("""file://[^\s)"']+"""),
            Regex("""(?i)\b(?:rtmp|rtmps|srt|rist|rtsp)://[^\s)"']+"""),
            Regex("""/storage/[^\s)"']+"""),
            Regex("""/data/(?:data|user/0|user_de/0)/[A-Za-z0-9._]+/[^\s)"']+"""),
            Regex("""(?i)\b[A-Z]:\\[^\r\n)"']+"""),
            Regex("""https?://[^\s)"']*\?[^\s)"']+"""),
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
        )

        /**
         * Redacts known-sensitive substrings from a single logcat line. Exposed
         * for testing — the goal is correctness, not perfect anonymization.
         */
        fun redactSensitive(line: String): String {
            var result = line
            for (pattern in SENSITIVE_PATTERNS) {
                result = pattern.replace(result, "<redacted>")
            }
            return result
        }
}
