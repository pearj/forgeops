package pearj.scripted

class ScriptedUtilities {
    static String cowSay(String message) {
        // There is space added to the front of the string which is why we need to add other spaces later
        return   "----------------------------------------------\n" +
                " Moo, " + message + "\n" +
                " ----------------------------------------------\n" +
                "      \\   ^__^\n" +
                "       \\  (oo)\\_______\n" +
                "          (__)\\ 0   0 )\\  *\n" +
                "              ||--0-w | \\/\n" +
                "              ||     ||        "
    }
}
