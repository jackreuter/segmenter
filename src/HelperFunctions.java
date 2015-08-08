//helper functions for segmenter

import java.util.Arrays;
import java.util.HashMap;
import java.io.PrintWriter;

public class HelperFunctions {

    public static final String PUNC = "@#$%^&*(){}[]|~`_-.,!?`~*^+=:;'/\\";
    public static final HashMap<String,String> ORD;
    static {
        ORD = new HashMap<String,String>();
        ORD.put("one","first");
        ORD.put("two","second");
        ORD.put("three","third");
        ORD.put("four","fourth");
        ORD.put("five","fifth");
        ORD.put("six","sixth");
        ORD.put("seven","seventh");
        ORD.put("eight","eighth");
        ORD.put("nine","ninth");
        ORD.put("ten","tenth");
        ORD.put("eleven","eleventh");
        ORD.put("twelve","twelfth");
        ORD.put("thirteen","thirteenth");
        ORD.put("fourteen","fourteenth");
        ORD.put("fifteen","fifteenth");
        ORD.put("sixteen","sixteenth");
        ORD.put("seventeen","seventeenth");
        ORD.put("eighteen","eighteenth");
        ORD.put("nineteen","nineteenth");
        ORD.put("twenty","twentieth");
        ORD.put("thirty","thirtieth");
        ORD.put("forty","fortieth");
        ORD.put("fifty","fiftieth");
        ORD.put("sixty","sixtieth");
        ORD.put("seventy","seventieth");
        ORD.put("eighty","eightieth");
        ORD.put("ninety","ninetieth");
        ORD.put("hundred","hundredth");
        ORD.put("thousand","thousandth");
        ORD.put("million","millionth");
        ORD.put("billion","billionth");
        ORD.put("trillion","trillionth");
    }
    public HelperFunctions () {}

    //known if lowercase or number or other heuristic, unknown if ALLCAPS
    public static boolean looksKnown(String s) {
        return (isLowerCase(s) || isNumber(s) || isOrdinal(s) || isPluralNumber(s) || (s.length()==1 && containsPunctuation(s)));
    }

    //most of these are pretty self-explanatory
    public static boolean isLowerCase(String s) {
        boolean couldBe = true;
        int i = 0;
        while (couldBe && i<s.length()) {
            if (!Character.isLowerCase(s.charAt(i))) {couldBe=false;}
            i++;
        }
        return couldBe;
    }

    public static boolean isNumber(String n) {
        try {
            for (int i=0; i<n.length(); i++) {
                int d = Integer.parseInt(n.charAt(i)+"");
            }
        } catch(NumberFormatException nfe) {
            return false;
        }
        return true;  
    }

    public static boolean isPluralNumber(String s) {
        if (isNumber(s.substring(0,s.length()-1)) && s.charAt(s.length()-1)=='s') {return true;}
        else {return false;}
    }
    
    public static boolean isOrdinal(String s) {
        int len = s.length();
        if (len<3) {return false;}
        else {
            if (!isNumber(s.substring(0,len-2))) {return false;}
            else {
                long n = Long.parseLong(s.substring(0,len-2));
                String e = s.substring(len-2).toLowerCase();
                if (!(e.equals("th")||e.equals("st")||e.equals("nd")||e.equals("rd"))) {return false;}
                else {
                    if (n%10==0 || n%10>3) {
                        if (e.equals("th")) {return true;}
                        else {return false;}
                    } else if (n%10==1) {
                        if (n%100==11) {
                            if (e.equals("th")) {return true;}
                            else {return false;}
                        } else {
                            if (e.equals("st")) {return true;}
                            else {return false;}
                                
                        }
                    } else if (n%10==2) {
                        if (n%100==12) {
                            if (e.equals("th")) {return true;}
                            else {return false;}
                        } else {
                            if (e.equals("nd")) {return true;}
                            else {return false;}
                        }
                    } else {
                        if (n%100==13) {
                            if (e.equals("th")) {return true;}
                            else {return false;}
                        } else {
                            if (e.equals("rd")) {return true;}
                            else {return false;}
                        }
                    }
                }
            }
        }
    }

    public static boolean contains(int[] arr, int x) {
        int[] copy = new int[arr.length];
        System.arraycopy(arr,0,copy,0,arr.length);
        Arrays.sort(copy);
        boolean found = false;
        boolean done = false;
        int i = 0;
        while (i<copy.length && !done) {
            if (copy[i]==x) {found=true;done=true;}
            if (copy[i]>x) {done=true;}
            i++;
        }
        return found;
    }

    public static boolean contains(String[] arr, String x) {
        String[] copy = new String[arr.length];
        System.arraycopy(arr,0,copy,0,arr.length);
        Arrays.sort(copy);
        boolean found = false;
        boolean done = false;
        int i = 0;
        while (i<copy.length && !done) {
            if (copy[i].equals(x)) {found=true;done=true;}
            if (copy[i].compareTo(x)==1) {done=true;}
            i++;
        }
        return found;
    }

    //converts binary string representation of a segmentation to actual segmentation
    //used to quickly get all possible segmentations
    public static String bin2seg(String b, String s) {
        String seg = "";
        for (int i=0; i<b.length(); i++) {
            if (b.charAt(i)=='0') {
                seg += s.charAt(i);
            } else {
                seg += s.charAt(i)+" ";
            }
        }
        seg += s.charAt(s.length()-1);
        return seg;
    }

    public static boolean containsNumber(String s) {
        boolean found = false;
        for (int i=0; i<s.length() && !found; i++) {
            if (isNumber(""+s.charAt(i))) {found=true;}
        }
        return found;
    }

    public static boolean containsPunctuation(String s) {
        boolean found = false;
        for (int i=0; i<s.length() && !found; i++) {
            if (PUNC.indexOf(s.charAt(i))!=-1) {found=true;}
        }
        return found;
    }

    //assumes that two adjacent words of length 1 should be concatenated
    //unless one or more is a number or punctuation
    public static String concatSingletons(String seg) {
        String[] words = seg.split(" ");
        String out = "";
        String singletons = "";
        for (String word : words) {
            if (word.length()>1 || isNumber(word) || containsPunctuation(word)) {
                if (singletons.length()==0) {out+=word+" ";}
                else {out+=singletons+" "+word+" ";}
                singletons="";
            } else {singletons+=word;}
        }
        out += singletons;
        return strip(out);
    }

    //removes spaces from end of string
    public static String strip(String s) {
        if (s.charAt(s.length()-1)==' ') {
            return strip(s.substring(0,s.length()-1));
        } else {return s;}
    }

    //concatenates two adjacent words if they form an ordinal
    //used to correct errors in segmentation by CamelCase
    public static String concatOrdinals(String seg) {
        String newSeg = "";
        String prev = "";
        for (String token : seg.split(" ")) {
            if (prev.equals("")) {newSeg+=token;}
            else {
                if (isOrdinal(prev+token)) {newSeg+=token;}
                else {newSeg+=" "+token;}
            }
            prev=token;
        }
        return newSeg;
    }

    //for mallet HMM file input
    public void writeCurrentSeg(String s) throws Exception {
        PrintWriter writer = new PrintWriter("current/seg");
        for (String token : s.split(" ")) {
            writer.println(token);
        }
        writer.close();
    }

    public void writeCurrentWord(String s) throws Exception {
        PrintWriter writer = new PrintWriter("current/word");
        for (char letter : s.toCharArray()) {
            writer.println(letter);
        }
        writer.println("END");
        writer.close();
    }
    
    public void writeCurrentLabels(String s) throws Exception {
        PrintWriter writer = new PrintWriter("current/labels");
        for (String token : s.split(" ")) {
            writer.println(token);
        }
        writer.close();
    }

    //for debugging of scoring functions/normalization
    public void printTop(HashMap<String,Double> map, int k) {
        HashMap<String,Double> copy = new HashMap<String,Double>(map);
        double bestScore = 0.0;
        String bestKey = "";
        for (HashMap.Entry entry : copy.entrySet()) {
            double score = (Double) entry.getValue();
            if (score>bestScore) {
                bestScore = score;
                bestKey = (String) entry.getKey();
            }
        }
        System.out.println(bestScore+"\t"+bestKey);
        if (k>1 && copy.size()>1) {
            copy.remove(bestKey);
            printTop(copy,k-1);
        } else {System.out.println();}
    }
    
}
