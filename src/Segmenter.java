// Segments hashtags

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Iterator;
import java.lang.Math;

public class Segmenter {

    private TrieST<String> wordTrie; //contains all words for matching
    private TrieST<String> transTrie; //translation dictionary
    private TrieST<Integer> twoGramTrie; //two grams with occurrence count
    private HashMap<String,Integer> possible; //unknowns segmented and # of occurences
    private HashSet<String> improbable; //manually vetoed unknowns
    private HelperFunctions hf;
    private EnglishNumberToWords num2words;
    private HMMHandler hh;
    private ArkHandler ah;
    private double L; //weight of length score
    private double T; //weight of 2GM score
    private double F; //weight of final score
    public static final int MIDS = 3; //# substrings found in long inputs. lower=faster/less accurate
    public static final double CAMEL_THRESHOLD = 20; //length score threshold to accept camelsegs
    public static final double LENGTH_THRESHOLD = 6; //length score threshold to consider segmenting
    public static final double WORD_THRESHOLD = 1.65; //chScore threshold for unknowns to be added to dictionary. depends on size of input file because frequency is taken into account. right now the value is pretty good for inputs of about 1000 hashtags
    public static final int PRUNE_L = 1000; //prune by length score
    private int PRUNE_LT = 5; //prune by convex combo of length and 2GM score

    //constructor takes three filenames as input:
    //matching dictionary, translation dictionary, and two gram table
    //plus weight ratios for final scoring function
    public Segmenter(String dictFile, String transFile, String twoGramFile, double l, double t, double f) throws Exception {
        L=l;
        T=t;
        F=f;

        num2words = new EnglishNumberToWords();
        hf = new HelperFunctions();
        ah = new ArkHandler();

        possible = new HashMap<String,Integer>();
        improbable = new HashSet<String>();
        BufferedReader br = new BufferedReader(new FileReader("../data/dictionaries/improbable"));
        String line = br.readLine();
        while (line != null) {
            improbable.add(line.toLowerCase());
            line = br.readLine();
        }
        
        //load up dictionary files into a TrieST
        System.out.print("Loading dictionaries...");
        br = new BufferedReader(new FileReader(dictFile));
        line = br.readLine();
        wordTrie = new TrieST<String>();
        while (line != null) {
            wordTrie.put(line.toLowerCase(),"");
            line = br.readLine();
        }
        transTrie = new TrieST<String>();
        br = new BufferedReader(new FileReader(transFile));
        line = br.readLine();
        while (line != null) {
            String[] entry = line.split(":");
            if (entry.length > 1) {
                transTrie.put(entry[0].toLowerCase(),entry[1].toLowerCase());
            } else {
                transTrie.put(entry[0].toLowerCase(),entry[0].toLowerCase());
            }
            line = br.readLine();
        }
        System.out.println("done");

        //load up twograms into a TrieST
        System.out.print("Loading two-grams...");
        twoGramTrie = new TrieST<Integer>();
        br = new BufferedReader(new FileReader(twoGramFile));
        line = br.readLine();
        while (line != null) {
            String[] entry = line.split("\t");
            twoGramTrie.put(entry[1].toLowerCase()+" "+entry[2].toLowerCase(),Integer.parseInt(entry[0]));
            line = br.readLine();
        }
        System.out.println("done");
        br.close();

        //load up HMMs into an HMMHandler
        System.out.print("Loading HMMs...");
        hh = new HMMHandler("../data/hmms/hmm1","../data/hmms/hmm2");
        System.out.println("done\n");

    }

    //segments a string using pipeline approach
    public String segmentByPipeline(String s) throws Exception {
        String seg = getSegmentByPipeline(s);
        if (seg.toLowerCase().equals(s.toLowerCase())) {return seg;}
        String newSeg = "";
        for (String token : seg.split(" ")) {
            if (hf.looksKnown(token)) {newSeg+=token+" ";}
            else {
                if (possible.containsKey(token)) {possible.put(token,possible.get(token)+1);}
                else {possible.put(token,1);}
                newSeg+=getSegmentByPipeline(token)+" ";
            }
        }
        return hf.concatSingletons(hf.strip(newSeg));
    }
    
    public String getSegmentByPipeline(String s) throws Exception {
        if (isKnown(s)) {return s.toLowerCase();}
        if (s.length()<2) {return s;}
        if (s.length()<LENGTH_THRESHOLD && !(hf.containsPunctuation(s) || hf.containsNumber(s))) {return s.toUpperCase();}
        if (looksLikeCamelCase(s)) {
            String camelSeg = hf.concatOrdinals(capitalizeByKnown(segmentByCamelCase(s)));
            if (checksOut(camelSeg)) {return camelSeg;}
            else {
                camelSeg = mergeUnknowns(camelSeg);
                if (camelSeg.toLowerCase().equals(s.toLowerCase())) {return getSegmentByPipeline(camelSeg.toLowerCase());}
                else {return camelSeg;}
            }
        }
        //get probable segs
        String[] segs = getProbableSegs(s);
        if (segs.length==0) {segs=getAllSegs(s);}
        HashMap<String,Double> lengthScores = getNormalizedLengthScores(segs);
        
        //prune down
        segs = prune(segs,lengthScores,PRUNE_L);
        HashMap<String,Double> twoGramScores = getNormalizedTwoGramScores(segs);
        HashMap<String,Double> weightedScores = new HashMap<String,Double>();
        //7 and 3 were found to be close to optimal weights for combination of length and 2GM scores
        for (String seg : segs) {weightedScores.put(seg,((7*lengthScores.get(seg)+3*twoGramScores.get(seg))/(7+3)));}

        //prune down
        segs = prune(segs,weightedScores,PRUNE_LT);
        if (segs.length==1) {return segs[0];}

        //for AH scoring, translation is considered, rather than segmentation itself
        //translation accuracy has not been tested and this may be flawed
        String[] trans = new String[segs.length];
        for (int i=0; i<segs.length; i++) {trans[i]=bestTwoGramTrans(segs[i]);}

        //choose final score, AH or HH
        HashMap<String,Double> finalScores = getNormalizedAHScores(segs,trans);
        //HashMap<String,Double> finalScores = getNormalizedHHScores(segs);
        
        double topScore = 0.0;
        String topSeg = "";
        for (String seg : segs) {
            //convex combination of scores
            double segScore = (L*lengthScores.get(seg)+T*twoGramScores.get(seg)+F*finalScores.get(seg))/(L+T+F);
            if (segScore >= topScore) {
                topScore = segScore;
                topSeg = seg;
            }
        }
        return topSeg;
    }

    public String[] prune(String[] segs, HashMap<String,Double> scores, int k) {
        ValueComparator bvc = new ValueComparator(scores);
        TreeMap<String,Double> sorted = new TreeMap<String,Double>(bvc);
        sorted.putAll(scores);
        if (scores.size()<k){k=scores.size();}
        String[] top = new String[k];
        for (int i=0; i<k; i++) {top[i]=(String) sorted.keySet().toArray()[i];}
        return top;
    }

    //used to check validity of segmentation by CamelCase
    //accepts by length threshold or if all parts are known
    //sometimes produces errors in over-segmentation when hashtags are stylized, i.e.
    //"ILoveMyBlackBerry"
    public boolean checksOut(String camelSeg) {
        boolean allKnown = true;
        for (String token : camelSeg.split(" ")) {
            if (!hf.looksKnown(token)) {
                allKnown=false;
            }
        }
        if (allKnown || lengthScore(camelSeg)>CAMEL_THRESHOLD) {return true;}
        else {return false;}
    }

    //generous check to see if a string my be delimited by camelcase
    public boolean looksLikeCamelCase(String s) {
        int up = 0;
        int lo = 0;
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) {up++;}
            else {lo++;}
        }
        if (up>1 && lo>1) {return true;}
        else {return false;}
    }

    //segments off numbers as words and punctuation as single characters
    public String segmentByCamelCase(String s) {
        String seg = "";
        String word = "";
        boolean number = false;
        for (char c : s.toCharArray()) {
            if (hf.PUNC.indexOf(c)!=-1) {
                if (word.length()>0) {seg+=word+" "+c+" ";word="";}
                else {seg+=c+" ";word="";}
                number = false;
            } else if (hf.isNumber(""+c)) {
                if (number) {word+=c;}
                else {
                    if (word.length()>0) {seg+=word+" ";}
                    word = ""+c;
                    number = true;
                }
            } else {
                if (number) {
                    seg+=word+" ";
                    word = ""+c;
                    number = false;
                }
                else {
                    if (Character.isUpperCase(c)) {
                        if (word.length()>0) {seg+=word+" ";}
                        word = ""+c;
                    } else {
                        word += c;
                    }
                }
            }
        }
        seg += word;
        return hf.concatSingletons(hf.strip(seg));
    }

    //merges adjacent unknown strings in a segmentation
    public String mergeUnknowns(String seg) {
        String newSeg = "";
        String unknown = "";
        for (String token : seg.split(" ")) {
            if (hf.looksKnown(token)) {
                if (unknown.length()>0) {
                    newSeg+=unknown+" "+token+" ";
                    unknown="";
                } else {
                    newSeg+=token+" ";
                }
            } else {
                unknown+=token;
            }
        }
        return hf.strip(newSeg+unknown);
    }

    //gets all possible segmentation, then filters out unlikely by some heuristics
    //will fail sometimes, e.g. "14me14u"
    public String[] getProbableSegs(String s) throws Exception {
        ArrayList<String> probable = new ArrayList<String>();
        String[] allSegs = getAllSegs(s);
        for (String seg : allSegs) {
            boolean looksOkay = true;
            String[] tokens = seg.split(" ");
            boolean prevWasNumber = false;
            int singletonCount = 0;
            for (int i=0; i<tokens.length && looksOkay; i++) {
                String token = tokens[i];
                boolean isNum = hf.isNumber(token);
                //to use MKM, uncomment following line
                //if (!isKnown(token)) {looksOkay=false;break;}
                if (isNum) {
                    if (prevWasNumber) {looksOkay=false;break;}
                    else {prevWasNumber=true;}
                } else {prevWasNumber=false;}
                if (token.length()>1 && hf.containsPunctuation(token)) {looksOkay=false;break;}
                if (!isKnown(token) && hf.containsNumber(token)) {looksOkay=false;break;}
                if (token.length()==1 && !isNum && !hf.containsPunctuation(token)) {singletonCount++;}
                else {singletonCount=0;}
                if (singletonCount>2) {looksOkay=false;break;}
            }
            if (looksOkay) {probable.add(seg);}
        }
        return probable.toArray(new String[probable.size()]);
    }

    //gets all possible segs by generating all possible binary strings of length s.length()-1
    public String[] getAllSegs(String s) throws Exception {
        int len = s.length();
        //gets too slow
        //finds the MIDS longest words in s and ensures that every segmentation contains at least one
        if (len>17) {
            int found = 0;
            ArrayList<String> segs = new ArrayList<String>();
            for (int i=len; i>=4 && found<MIDS; i--) {
                for (int j=0; i+j<=len && found<MIDS; j++) {
                    String mid = s.substring(j,j+i).toLowerCase();
                    if (isKnown(mid)) {
                        String beg = s.substring(0,j);
                        String end = s.substring(j+i,len);
                        if (beg.length()>0) {beg=segmentByPipeline(beg)+" ";}
                        if (end.length()>0) {end=" "+segmentByPipeline(end);}
                        segs.add(beg+mid+end);
                        found++;
                    }
                }
            }
            if (segs.size()==0) {return new String[]{s};}
            return segs.toArray(new String[segs.size()]);
        } else {
            if (len==1) {return new String[]{s};}
            int total = (int) Math.pow(2,len-1);
            String[] segs = new String[total];
            for (int i=0; i<total; i++) {
                String binary = Integer.toBinaryString(i);
                while (binary.length()<len-1) {
                    binary = "0"+binary;
                }
                String seg = capitalizeByKnown(hf.bin2seg(binary,s));
                segs[i] = seg;
            }
            return segs;
        }
    }

    //all these normalize functions do the same thing:
    //find highest score, divide all scores by highest
    //they could probably be presented more elegantly using an interface or something
    public HashMap<String,Double> getNormalizedLengthScores(String[] segs) {
        HashMap scores = new HashMap<String,Double>();
        double topScore = 0.0;
        for (String seg : segs) {
            double score = lengthScore(seg);
            if (score>topScore) {topScore=score;}
            scores.put(seg,score);
        }
        Iterator itr = scores.entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) itr.next();
            scores.put((String) pair.getKey(), (double) pair.getValue()/topScore);
        }
        return scores;
    }

    //when 2GM score not applicable, normalized to 0.5
    public HashMap<String,Double> getNormalizedTwoGramScores(String[] segs) {
        HashMap scores = new HashMap<String,Double>();
        double topScore = 1.0;
        for (String seg : segs) {
            double score = twoGramScore(seg);
            if (score>topScore) {topScore=score;}
            scores.put(seg,score);
        }
        Iterator itr = scores.entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) itr.next();
            double newScore;
            double oldScore = (Double) pair.getValue();
            if (oldScore<0.0) {newScore=0.5;}
            else {newScore=oldScore/topScore;}
            scores.put((String) pair.getKey(), newScore);
        }
        return scores;
    }
    
    public HashMap<String,Double> getNormalizedHHScores(String[] segs) throws Exception {
        HashMap scores = new HashMap<String,Double>();
        double topScore = 1.0;
        for (String seg : segs) {
            double score = hhScore(seg);
            if (score>topScore) {topScore=score;}
            scores.put(seg,score);
        }
        Iterator itr = scores.entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) itr.next();
            scores.put((String) pair.getKey(), (double) pair.getValue()/topScore);
        }
        return scores;
    }

    public HashMap<String,Double> getNormalizedAHScores(String[] segs, String[] trans) throws Exception {
        HashMap scores = new HashMap<String,Double>();
        double topScore = 1.0;
        for (int i=0; i<segs.length; i++) {
            double score = ahScore(trans[i]);
            if (score>topScore) {topScore=score;}
            scores.put(segs[i],score);
        }
        Iterator itr = scores.entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) itr.next();
            scores.put((String) pair.getKey(), (double) pair.getValue()/topScore);
        }
        return scores;
    }

    public HashMap<String,Double> getNormalizedCHScores(String[] segs, String[] trans) throws Exception {
        HashMap scores = new HashMap<String,Double>();
        double topScore = 1.0;
        for (int i=0; i<segs.length; i++) {
            double score = chScore(trans[i]);
            if (score>topScore) {topScore=score;}
            scores.put(segs[i],score);
        }
        Iterator itr = scores.entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) itr.next();
            scores.put((String) pair.getKey(), (double) pair.getValue()/topScore);
        }
        return scores;
    }

    //sum of lengths of unknown words and squares of lengths of known words divided by
    //number of words excluding punctuation
    public double lengthScore(String seg) {
        String[] tokens = seg.split(" ");
        int punc = 0;
        double score = 1.0;
        for (String token : tokens) {
            if (token.length()==1 && hf.containsPunctuation(token)) {
                punc++;
            } else {
                if (hf.isLowerCase(token)) {
                    score += token.length()*token.length();
                } else {
                    score += token.length();
                }
            }
        }
        if (tokens.length-punc==0) {score = tokens.length;}
        else {score = score/(tokens.length-punc);}
        return score;
    }

    //gets all possible translations of a seg
    //scores each translation and returns highest score
    public double twoGramScore(String seg) {
        if (seg.split(" ").length==1) {return -1.0;}
        ArrayList<String> translations = getAllSegTranslations(seg);
        double best = 0.0;
        String bestTran = "";
        for (String trans : translations) {
            double score = twoGramScoreTrans(trans);
            if (trans.equals(seg)) {score*=2;}
            if (score>best) {best=score;bestTran=trans;}
        }
        return best;
    }

    public ArrayList<String> getAllSegTranslations(String seg) {
        ArrayList<ArrayList<String>> tokenTranslations = new ArrayList<ArrayList<String>>();
        String[] tokens = seg.split(" ");
        for (String token : tokens) {
            tokenTranslations.add(getAllTokenTranslations(token));
        }
        return getAllSegTranslationsRec(tokenTranslations);
    }

    public ArrayList<String> getAllSegTranslationsRec(ArrayList<ArrayList<String>> tt) {
        if (tt.size()==1) {
            return tt.get(0);
        } else {
            ArrayList<String> first = tt.remove(0);
            ArrayList<String> bigTrans = new ArrayList<String>();
            ArrayList<String> smallTrans = getAllSegTranslationsRec(tt);
            for (String s : first) {
                for (String t : smallTrans) {
                    bigTrans.add(s+" "+t);
                }
            }
            return bigTrans;
        }
    }

    //translates a word based on heuristics or translation dictionary
    //runs into errors with decades right now I think, e.g. "80s" doesn't give back "eighties"
    public ArrayList<String> getAllTokenTranslations(String s) {
        ArrayList<String> trans = new ArrayList<String>();
        trans.add(s);
        if (s.length()==0) {return trans;}
        if (hf.isNumber(s) && s.length()<10) {trans.add(num2words.convert(Integer.parseInt(s)));}
        if (hf.isOrdinal(s)) {trans.add(ordinal2words(s));}
        if (transTrie.contains(s)) {
            String[] all = transTrie.get(s).split(",");
            for (String translation : all) {
                trans.add(translation);
            }
        }
        return trans;
    }

    //logic is a bit awkward b/c went through many revisions
    //right now just returns found/total
    public double twoGramScoreTrans(String seg) {
        double totalScore = 0.0;
        String[] tokens = seg.split(" ");
        int found = 0;
        int i = 0;
        while (i<tokens.length-1) {
            String key = tokens[i].toLowerCase()+" "+tokens[i+1].toLowerCase();
            double score = 0.0;
            if (twoGramTrie.contains(key)) {
                score = 1.0;
            }
            if (score>0.0) {found++;}
            totalScore += score;
            i++;
        }
        if (tokens.length-1>1) {totalScore = totalScore/(tokens.length-1);}
        return totalScore;
    }

    //doesn't waste time calculating if the weight is 0
    //right now the segmenter is only built to handle one of the hmm scores, hh or ah
    //they are weighted by F and called the final score
    public double hhScore(String seg) throws Exception {
        if (F>0) {
            return hh.scoreSeg(seg);
        } else {
            return 1.0;
        }
    }
    
    public double ahScore(String seg) throws Exception {
        if (F>0) {
            return ah.ahScore(seg);
        } else {
            return 1.0;
        }
    }

    //used to determine which unknowns should be added to the dictionary
    //just average probability of character sequence in a markov chain
    //trained on just the words section of the dictionary
    public double chScore(String seg) throws Exception{
        double score = 1.0;
        for (String word : seg.split(" ")) {
            score *= hh.scoreWord(word);
        }
        return score;
    }

    //checks if string is in either matching or tranlation dictionary, or is known heuristically
    public boolean isKnown(String s) {
        String l = s.toLowerCase();
        return (wordTrie.contains(l) || transTrie.contains(l) || hf.isNumber(l) || hf.isOrdinal(l) || hf.isPluralNumber(l) || (s.length()==1 && hf.containsPunctuation(l)));
    }

    //capitalizes unknown tokens, lowercase for known
    public String capitalizeByKnown(String s) {
        String[] tokens = s.split(" ");
        String newString = "";
        for (String token : tokens) {
            if (isKnown(token)) {newString+=token.toLowerCase()+" ";}
            else {newString+=token.toUpperCase()+" ";}
        }
        return newString.substring(0,newString.length()-1);
    }

    //getter function for possible uknowns
    public HashMap<String,Integer> getPossible() {
        return possible;
    }

    //goes through possible and adds those that break scoring threshold to the dictionary
    public void addProbableToDict() throws Exception {
        FileWriter probable = new FileWriter ("../data/dictionaries/probable",true);
        FileWriter total = new FileWriter ("../data/dictionaries/total",true);
        for (String key : possible.keySet()) {
            double wordScore = hh.scoreWord(key)*possible.get(key);
            if (wordScore>WORD_THRESHOLD && !isKnown(key.substring(1,key.length())) && !isKnown(key.substring(0,key.length()-1)) && !improbable.contains(key.toLowerCase())) {
                probable.write(key+"\n");
                total.write(key+"\n");
            }
        }
        probable.close();
        total.close();
    }

    //gets word form of ordinal, i.e. "20th" => "twentieth"
    public String ordinal2words(String s) {
        String num = num2words.convert(Integer.parseInt(s.substring(0,s.length()-2)));
        String ordinal = "";
        String[] words = num.split(" ");
        for (int i=0; i<words.length; i++) {
            if (i==words.length-1) {
                if (hf.ORD.keySet().contains(words[i])) {ordinal+=hf.ORD.get(words[i]);}
                else {ordinal+=words[i]+"th";}
            }
            else {ordinal+=words[i]+" ";}
        }
        return ordinal;
    }

    //returns best translation of a segmentation based on 2GM score
    public String bestTwoGramTrans(String seg) {
        if (seg.length()==1) {return seg;}
        ArrayList<String> translations = getAllSegTranslations(seg);
        double best = 0.0;
        String bestTran = seg;
        for (String trans : translations) {
            double score = twoGramScoreTrans(trans);
            if (trans.equals(seg)) {score*=2;}
            if (score>best) {best=score;bestTran=trans;}
        }
        return bestTran;
    }

}
