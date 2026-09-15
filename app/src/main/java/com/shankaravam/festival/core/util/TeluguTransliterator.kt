package com.shankaravam.festival.core.util

/**
 * Lightweight, 100% offline, zero-dependency phonetic transliteration engine
 * for Telugu names (AGENTS.md Rule #1 & #2).
 *
 * Designed specifically for Indian/Telugu donor names and pandal mic announcements:
 * - South Indian Initials: "R." / "R" → "ఆర్.", "K. V." / "K V" → "కే. వి.", "M." → "ఎం."
 * - Common Telugu Surnames & Honorifics: Reddy, Chowdary, Naidu, Goud, Rao, Sri, Lakshmi, Sandeep
 * - Anusvara clusters: "nd" → "ంద", "nth" → "ంత", "mb" → "ంబ", "mp" → "ంప", "ng" → "ంగ"
 * - Telugu Script Passthrough: Existing Telugu text (\u0C00..\u0C7F) is preserved verbatim
 * - Punctuation Safety: Hyphens are normalized to spaces so native TTS never says "డాష్"
 *
 * Pure JVM, main-thread safe (<0.2 ms per name), no Android Context required.
 */
object TeluguTransliterator {

    private val INITIALS_MAP = mapOf(
        'a' to "ఏ.", 'b' to "బి.", 'c' to "సి.", 'd' to "డి.",
        'e' to "ఈ.", 'f' to "ఎఫ్.", 'g' to "జి.", 'h' to "హెచ్.",
        'i' to "ఐ.", 'j' to "జె.", 'k' to "కే.", 'l' to "ఎల్.",
        'm' to "ఎం.", 'n' to "ఎన్.", 'o' to "ఓ.", 'p' to "పి.",
        'q' to "క్యూ.", 'r' to "ఆర్.", 's' to "ఎస్.", 't' to "టి.",
        'u' to "యు.", 'v' to "వి.", 'w' to "డబ్ల్యూ.", 'x' to "ఎక్స్.",
        'y' to "వై.", 'z' to "జెడ్."
    )

    private val SPECIAL_WORDS = mapOf(
        // Surnames / Castes / Honorifics
        "sri" to "శ్రీ", "shri" to "శ్రీ", "shree" to "శ్రీ",
        "rao" to "రావు", "row" to "రావు",
        "reddy" to "రెడ్డి", "reddi" to "రెడ్డి",
        "chowdary" to "చౌదరి", "choudary" to "చౌదరి", "chaudary" to "చౌదరి", "chowdari" to "చౌదరి",
        "swamy" to "స్వామి", "swami" to "స్వామి",
        "naidu" to "నాయుడు", "nayudu" to "నాయుడు",
        "goud" to "గౌడ్", "gowd" to "గౌడ్",
        "raju" to "రాజు",
        "varma" to "వర్మ", "sharma" to "శర్మ",
        "gupta" to "గుప్తా",
        "kumar" to "కుమార్", "kumari" to "కుమారి",
        "prasad" to "ప్రసాద్",
        "babu" to "బాబు",
        "garu" to "గారు",
        "lakshmi" to "లక్ష్మి", "laxmi" to "లక్ష్మి",
        "sandeep" to "సందీప్", "sandip" to "సందీప్",
        "suresh" to "సురేష్", "ramesh" to "రమేష్", "mahesh" to "మహేష్",
        "rajesh" to "రాజేష్", "venkatesh" to "వెంకటేష్", "satish" to "సతీష్",
        "naresh" to "నరేష్", "lokesh" to "లోకేష్", "dinesh" to "దినేష్",
        "sita" to "సీత", "seetha" to "సీత", "geetha" to "గీత", "gita" to "గీత",
        "srinivas" to "శ్రీనివాస్", "srinivasa" to "శ్రీనివాస", "srinu" to "శ్రీను",
        "krishna" to "కృష్ణ", "rama" to "రామ", "shiva" to "శివ", "sai" to "సాయి",
        "devi" to "దేవి", "durga" to "దుర్గ", "anand" to "ఆనంద్",

        // Specific Telugu Compound Names
        "venkat" to "వెంకట్",
        "venkatreddy" to "వెంకట్ రెడ్డి",
        "narsireddy" to "నర్సిరెడ్డి",
        "narsigoud" to "నర్సిగౌడ్",
        "narsi" to "నర్సి",
        "narsaiah" to "నర్సయ్య", "narasaiah" to "నరసయ్య",
        "narsimha" to "నర్సింహ", "narasimha" to "నరసింహ", "narsimlu" to "నర్సింలు",
        "ramreddy" to "రామ్ రెడ్డి", "ramareddy" to "రామారెడ్డి", "krishnareddy" to "కృష్ణారెడ్డి",
        "subbarao" to "సుబ్బారావు", "ramarao" to "రామారావు", "krishnarao" to "కృష్ణారావు",
        "rambabu" to "రాంబాబు", "venkatbabu" to "వెంకట్ బాబు",
        "sivaprasad" to "శివప్రసాద్", "ramprasad" to "రామ్ ప్రసాద్", "durgaprasad" to "దుర్గాప్రసాద్",
        "varaprasad" to "వరప్రసాద్", "hariprasad" to "హరిప్రసాద్",
        "vijaykumar" to "విజయ్ కుమార్", "ramkumar" to "రామ్ కుమార్", "sivakumar" to "శివకుమార్",
        "ajaykumar" to "అజయ్ కుమార్", "anilkumar" to "అనిల్ కుమార్", "sunilkumar" to "సునిల్ కుమార్",
        "praveenkumar" to "ప్రవీణ్ కుమార్",
        "sridevi" to "శ్రీదేవి", "ramadevi" to "రమాదేవి", "sitadevi" to "సీతాదేవి",
        "laxmidevi" to "లక్ష్మీదేవి", "lakshmidevi" to "లక్ష్మీదేవి",
        "venkatamma" to "వెంకటమ్మ", "ramulamma" to "రాములమ్మ", "lakshmamma" to "లక్ష్మమ్మ",
        "narsamma" to "నర్సమ్మ", "gangamma" to "గంగమ్మ",
        "ramaiah" to "రామయ్య", "ramayya" to "రామయ్య",
        "venkataiah" to "వెంకటయ్య", "venkatayya" to "వెంకటయ్య",
        "mallaiah" to "మల్లయ్య", "lingaiah" to "లింగయ్య", "laxmaiah" to "లక్ష్మయ్య", "pullaiah" to "పుల్లయ్య",
        "satyanarayana" to "సత్యనారాయణ",
        "venkataramana" to "వెంకటరమణ",
        "venkateswarlu" to "వెంకటేశ్వర్లు",
        "anjaneyulu" to "ఆంజనేయులు",
        "hanumanthu" to "హనుమంతు",
        "mallesh" to "మల్లేష్", "mallesham" to "మల్లేశం",
        "chandrasekhar" to "చంద్రశేఖర్",
        "somesh" to "సోమేశ్", "harish" to "హరీష్", "girish" to "గిరీష్",
        "santosh" to "సంతోష్", "santhosh" to "సంతోష్",

        // Indian Names ending in -nder / -ndra
        "ravinder" to "రవీందర్", "ravindra" to "రవీంద్ర",
        "surender" to "సురేందర్", "surendra" to "సురేంద్ర",
        "narender" to "నరేందర్", "narendra" to "నరేంద్ర",
        "mahender" to "మహేందర్", "mahendra" to "మహేంద్ర",
        "devender" to "దేవేందర్", "devendra" to "దేవేంద్ర",
        "upender" to "ఉపేందర్", "upendra" to "ఉపేంద్ర",
        "rajender" to "రాజేందర్", "rajendra" to "రాజేంద్ర",
        "nagender" to "నాగేందర్", "nagendra" to "నాగేంద్ర",
        "gajender" to "గజేందర్", "gajendra" to "గజేంద్ర",
        "raghavendra" to "రాఘవేంద్ర",
        "phanindra" to "ఫణీంద్ర",
        "dharmender" to "ధర్మేందర్", "dharmendra" to "ధర్మేంద్ర",
        "joginder" to "జోగిందర్",
        "veerender" to "వీరేందర్", "virender" to "వీరేందర్", "veerendra" to "వీరేంద్ర", "virendra" to "వీరేంద్ర",
        "satender" to "సతేందర్"
    )

    private val COMPOUND_SUFFIXES = listOf(
        // Surnames / Castes
        "chowdary" to "చౌదరి", "choudary" to "చౌదరి", "chaudary" to "చౌదరి", "chowdari" to "చౌదరి",
        "reddy" to "రెడ్డి", "reddi" to "రెడ్డి",
        "naidu" to "నాయుడు", "nayudu" to "నాయుడు",
        "swamy" to "స్వామి", "swami" to "స్వామి",
        "prasad" to "ప్రసాద్",
        "kumar" to "కుమార్", "kumari" to "కుమారి",
        "sharma" to "శర్మ", "varma" to "వర్మ",
        "gupta" to "గుప్తా",
        "goud" to "గౌడ్", "gowd" to "గౌడ్",
        "babu" to "బాబు",
        "raju" to "రాజు",
        "garu" to "గారు",
        "devi" to "దేవి",
        "rao" to "రావు", "row" to "రావు",
        // Kinship / Sandhi suffixes
        "amma" to "అమ్మ",
        "anna" to "అన్న",
        "appa" to "అప్ప",
        "aiah" to "య్య", "ayya" to "య్య", "iah" to "య్య"
    )

    private val KINSHIP_SANDHI_SUFFIXES = setOf("amma", "anna", "appa", "aiah", "ayya", "iah")

    // Independent Vowels at word/syllable boundary
    private val VOWELS_START = listOf(
        "aa" to "ఆ", "ee" to "ఈ", "ii" to "ఈ", "oo" to "ఊ", "uu" to "ఊ",
        "ai" to "ఐ", "au" to "ఔ", "ou" to "ఔ", "ow" to "ఔ", "aw" to "ఔ",
        "ae" to "ఏ", "a" to "అ", "i" to "ఇ", "u" to "ఉ", "e" to "ఎ", "o" to "ఒ"
    )

    // Dependent Vowel Signs (Matras) attached to a consonant
    private val MATRAS = listOf(
        "aa" to "\u0C3E", // ా
        "ee" to "\u0C40", // ీ
        "ii" to "\u0C40", // ీ
        "oo" to "\u0C42", // ూ
        "uu" to "\u0C42", // ూ
        "ai" to "\u0C48", // ై
        "au" to "\u0C4C", // ౌ
        "ou" to "\u0C4C", // ౌ
        "ow" to "\u0C4C", // ౌ
        "aw" to "\u0C4C", // ౌ
        "ae" to "\u0C47", // ే
        "a" to "",        // inherent vowel 'a' (no matra, removes virama)
        "i" to "\u0C3F", // ి
        "u" to "\u0C41", // ు
        "e" to "\u0C46", // ె
        "o" to "\u0C4A"  // ొ
    )

    // Consonant clusters & digraphs ordered by longest match first.
    // Includes geminates (dd -> డ్డ for Reddy, tt -> ట్ట, etc.) and nasal anusvara clusters.
    private val CONSONANTS = listOf(
        // Aspirates & special clusters
        "ksh" to "క్ష", "chh" to "ఛ",
        "ch" to "చ", "kh" to "ఖ", "gh" to "ఘ", "jh" to "ఝ",
        "nth" to "ంత", "nd" to "ంద", "mp" to "ంప", "mb" to "ంబ", "ng" to "ంగ", "nk" to "ంక",
        "th" to "త", "dh" to "ధ", "ph" to "ఫ", "bh" to "భ", "sh" to "శ",
        // Geminates / Vatthulu (dd -> డ్డ for Reddy/Guddi, tt -> ట్ట for Potti/Chitti, etc.)
        "ddh" to "ద్ధ", "dd" to "డ్డ",
        "tth" to "త్థ", "tt" to "ట్ట",
        "nn" to "న్న", "ll" to "ల్ల", "mm" to "మ్మ", "pp" to "ప్ప", "yy" to "య్య",
        "bb" to "బ్బ", "kk" to "క్క", "gg" to "గ్గ", "jj" to "జ్జ",
        // Single consonants
        "k" to "క", "g" to "గ", "j" to "జ", "t" to "ట", "d" to "ద",
        "n" to "న", "p" to "ప", "f" to "ఫ", "b" to "బ", "m" to "మ",
        "y" to "య", "r" to "ర", "l" to "ల", "v" to "వ", "w" to "వ",
        "s" to "స", "h" to "హ"
    )

    private const val VIRAMA = "\u0C4D" // ్

    /**
     * Transliterates [input] to Telugu script.
     * Returns empty string for null, blank, or whitespace-only inputs.
     */
    fun transliterate(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()

        // Normalize dashes (hyphen, en-dash, em-dash, etc.) to spaces so native TTS never speaks "dash"
        val dashCleaned = trimmed.replace(Regex("[-\\u2010-\\u2015]"), " ")

        // If input is purely Telugu already, return clean normalized Telugu as-is
        if (dashCleaned.all { isTeluguOrWhitespace(it) }) {
            return dashCleaned.replace(Regex("\\s+"), " ")
        }

        // Normalize contiguous initials like "K.V." -> "K. V."
        val normalized = trimmed
            .replace(Regex("([A-Za-z])\\.([A-Za-z])"), "$1. $2")
            .replace(Regex("[-\u2013\u2014]"), " ") // Map hyphen/en-dash/em-dash to spaces (zero dashes)
            .replace(Regex("\\s+"), " ")

        val words = normalized.split(" ")
        return words.joinToString(" ") { word ->
            transliterateWord(word)
        }.trim()
    }

    private fun transliterateWord(rawWord: String): String {
        val word = rawWord.trim()
        if (word.isEmpty()) return ""

        // Preserve already-Telugu words
        if (word.all { isTeluguOrWhitespace(it) }) return word

        // Check single-letter initials: "R.", "R", "k", "k."
        val lower = word.lowercase()
        if (lower.length == 1 && lower[0] in INITIALS_MAP) {
            return INITIALS_MAP[lower[0]]!!
        }
        if (lower.length == 2 && lower.endsWith(".") && lower[0] in INITIALS_MAP) {
            return INITIALS_MAP[lower[0]]!!
        }

        // Check special name vocabulary (exact match)
        val cleanWord = lower.removeSuffix(".")
        SPECIAL_WORDS[cleanWord]?.let { return it }

        // Check compound surname/caste/honorific suffixes
        for ((suffixLatin, suffixTelugu) in COMPOUND_SUFFIXES) {
            if (cleanWord.length > suffixLatin.length + 1 && cleanWord.endsWith(suffixLatin)) {
                val prefix = cleanWord.substring(0, cleanWord.length - suffixLatin.length)
                val prefixTelugu = transliterateWordStem(prefix)
                val endsWithConsonant = prefix.last() !in "aeiou"

                return if (suffixLatin in KINSHIP_SANDHI_SUFFIXES) {
                    val basePrefix = if (prefixTelugu.endsWith(VIRAMA)) prefixTelugu.removeSuffix(VIRAMA) else prefixTelugu
                    val baseSuffix = if (suffixTelugu.startsWith("అ")) suffixTelugu.removePrefix("అ") else suffixTelugu
                    "$basePrefix$baseSuffix"
                } else if (!endsWithConsonant) {
                    // Vowel-ending prefix blends smoothly: "నర్సి" + "రెడ్డి" -> "నర్సిరెడ్డి"
                    "$prefixTelugu$suffixTelugu"
                } else {
                    // Consonant-ending prefix has natural word break: "వెంకట్" + "రెడ్డి" -> "వెంకట్ రెడ్డి"
                    "$prefixTelugu $suffixTelugu"
                }
            }
        }

        return transliterateWordStem(cleanWord)
    }

    private fun transliterateWordStem(cleanWord: String): String {
        SPECIAL_WORDS[cleanWord]?.let { return it }

        // Syllabic phonetic parsing (longest-match greedy parser)
        val sb = StringBuilder()
        var i = 0
        val len = cleanWord.length

        while (i < len) {
            val char = cleanWord[i]

            // If it's an existing Telugu char or punctuation, append verbatim
            if (isTelugu(char) || !char.isLetter()) {
                sb.append(char)
                i++
                continue
            }

            // Word-final 'y' after consonant acts as vowel 'i' (e.g. reddy, swamy, chowdary)
            if (i == len - 1 && char == 'y' && sb.endsWith(VIRAMA)) {
                sb.setLength(sb.length - VIRAMA.length)
                sb.append("\u0C3F") // ి
                i++
                continue
            }

            // Handle Indian name endings: "-nder" -> "ందర్", "-ndra" -> "ంద్ర"
            if (i + 4 == len && cleanWord.startsWith("nder", i)) {
                sb.append("ందర్")
                i += 4
                continue
            }
            if (i + 4 == len && cleanWord.startsWith("ndra", i)) {
                sb.append("ంద్ర")
                i += 4
                continue
            }

            // If at the start of a word or after a vowel, check independent vowels
            val isStartOrAfterVowel = (i == 0 || sb.isEmpty() || !sb.endsWith(VIRAMA))
            var matchedVowel = false
            if (isStartOrAfterVowel) {
                for ((latin, teluguVowel) in VOWELS_START) {
                    if (cleanWord.startsWith(latin, i)) {
                        sb.append(teluguVowel)
                        i += latin.length
                        matchedVowel = true
                        break
                    }
                }
            }
            if (matchedVowel) continue

            // Match consonant
            var matchedConsonant = false
            for ((latin, teluguCons) in CONSONANTS) {
                if (cleanWord.startsWith(latin, i)) {
                    i += latin.length

                    // Check if followed by a vowel
                    var matchedMatra = false
                    for ((vowelLatin, matra) in MATRAS) {
                        if (cleanWord.startsWith(vowelLatin, i)) {
                            sb.append(teluguCons)
                            sb.append(matra)
                            i += vowelLatin.length
                            matchedMatra = true
                            break
                        }
                    }

                    if (!matchedMatra) {
                        // Consonant without vowel: append consonant + virama
                        // (handles conjuncts/vatthulu: next consonant attaches to it)
                        sb.append(teluguCons)
                        sb.append(VIRAMA)
                    }

                    matchedConsonant = true
                    break
                }
            }

            if (!matchedConsonant) {
                // Fallback: unmapped Latin letter
                sb.append(char)
                i++
            }
        }

        return sb.toString()
    }

    private fun isTelugu(c: Char): Boolean = c in '\u0C00'..'\u0C7F'

    private fun isTeluguOrWhitespace(c: Char): Boolean = isTelugu(c) || c.isWhitespace() || c == '.' || c == ','
}
