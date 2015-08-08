import java.util.Arrays;
import java.io.PrintWriter;

public class Evaluator {

    private String[] proposed;
    private String[] correct;
    private static HelperFunctions hf;
    
    //takes in an array of proposed segmentations and an array of correct ones
    public Evaluator(String[] p, String[] c) {
        proposed = p;
        correct = c;
        hf = new HelperFunctions();
    } 

    //calculates precision, recall, f-score,
    //known precision, known recall
    //unknown precision, uknown recall
    //known and unknown are confident and weak in variable names
    //they are simply the measures taken in terms of known or unknown words, as opposed to all words
    public void evaluate() throws Exception {
        PrintWriter wrong = new PrintWriter("wrong");
        double prec = 0.0;
        double rec = 0.0;
        double confPrec = 0.0;
        double confRec = 0.0;
        double weakPrec = 0.0;
        double weakRec = 0.0;
  
        int i = 0;
        while (i<proposed.length && i<correct.length) {
            double lineP = precision(proposed[i],correct[i]);
            double lineR = recall(proposed[i],correct[i]);
            double lineCP = confidentPrecision(proposed[i],correct[i]);
            double lineCR = confidentRecall(proposed[i],correct[i]);
            double lineWP = weakPrecision(proposed[i],correct[i]);
            double lineWR = weakRecall(proposed[i],correct[i]);

            prec += lineP;
            rec += lineR;
            confPrec += lineCP;
            confRec += lineCR;
            weakPrec += lineWP;
            weakRec += lineWR;
            if (lineP<1.0 && lineR<1.0) {wrong.println(proposed[i]+"\t"+correct[i]);}
            i++;
        }
        wrong.close();
        double total = (double) i;
        prec = prec/total;
        rec = rec/total;
        confPrec = confPrec/total;
        confRec = confRec/total;
        weakPrec = weakPrec/total;
        weakRec = weakRec/total;
        double fmeas = 2*prec*rec/(prec+rec);

        //verbose form
        System.out.println("Precision: "+prec);
        System.out.println("Recall: "+rec);
        System.out.println("F-measure: "+fmeas);
        System.out.println("Confident Precision: "+confPrec);
        System.out.println("Confident Recall: "+confRec);
        System.out.println("Weak Precision: "+weakPrec);
        System.out.println("Weak Recall: "+weakRec);

        //concise form
        //System.out.println(prec+","+rec+","+fmeas+","+confPrec+","+confRec+","+weakPrec+","+weakRec);

    }

    //evaluation metrics
    //could be coded more elegantly as interface or class or something
    public static double precision(String p, String c) {
        double best = 0.0;
        for (String correct : c.split(",")) {
            double precision = getPrecision(p,correct);
            if (precision>best) {best=precision;}
        }
        return best;
    }
    public static double recall(String p, String c) {
        double best = 0.0;
        for (String correct : c.split(",")) {
            double recall = getRecall(p,correct);
            if (recall>best) {best=recall;}
        }
        return best;
    }
    public static double confidentPrecision(String p, String c) {
        double best = 0.0;
        for (String correct : c.split(",")) {
            double confidentPrecision = getConfidentPrecision(p,correct);
            if (confidentPrecision>best) {best=confidentPrecision;}
        }
        return best;
    }
    public static double confidentRecall(String p, String c) {
        double best = 0.0;
        for (String correct : c.split(",")) {
            double confidentRecall = getConfidentRecall(p,correct);
            if (confidentRecall>best) {best=confidentRecall;}
        }
        return best;
    }
    public static double weakPrecision(String p, String c) {
        double best = 0.0;
        for (String correct : c.split(",")) {
            double weakPrecision = getWeakPrecision(p,correct);
            if (weakPrecision>best) {best=weakPrecision;}
        }
        return best;
    }
    public static double weakRecall(String p, String c) {
        double best = -1.0;
        for (String correct : c.split(",")) {
            double weakRecall = getWeakRecall(p,correct);
            if (weakRecall>best) {best=weakRecall;}
        }
        return best;
    }

    //actual calculation of metrics
    public static double getPrecision(String p, String c) {
        return countMatches(p,c)/p.split(" ").length;
    }
    public static double getRecall(String p, String c) {
        return countMatches(p,c)/c.split(" ").length;
    }
    public static double getConfidentPrecision(String p, String c) {
        double total = 0.0;
        for (String token : p.split(" ")) {
            if (hf.looksKnown(token)) {
                total += 1.0;
            }
        }
        if (total>0) {return countConfidentMatches(p,c)/total;}
        else {return 1.0;}
    }
    public static double getConfidentRecall(String p, String c) {
        double total = 0.0;
        for (String token : p.split(" ")) {
            if (hf.looksKnown(token)) {
                total += 1.0;
            }
        }
        if (total>0) {return countConfidentMatches(p,c)/c.split(" ").length;}
        else {return 0.0;}
    }
    public static double getWeakPrecision(String p, String c) {
        double total = 0.0;
        for (String token : p.split(" ")) {
            if (!hf.looksKnown(token)) {
                total += 1.0;
            }
        }
        if (total>0) {return countWeakMatches(p,c)/total;}
        else {return 1.0;}
    }
    public static double getWeakRecall(String p, String c) {
        double total = 0.0;
        for (String token : p.split(" ")) {
            if (!hf.looksKnown(token)) {
                total += 1.0;
            }
        }
        if (total>0) {return countWeakMatches(p,c)/c.split(" ").length;}
        else {return 0.0;}
    }

    //helper functions
    public static double countMatches(String p, String c) {
        double count = 0.0;
        String[] correctTokens = c.toLowerCase().split(" ");
        for (String token : p.split(" ")) {
            if (hf.contains(correctTokens,token.toLowerCase())) {count++;}
        }
        return count;
    }
    public static double countConfidentMatches(String p, String c) {
        double count = 0.0;
        String[] correctTokens = c.toLowerCase().split(" ");
        for (String token : p.split(" ")) {
            if (hf.looksKnown(token))
                if (hf.contains(correctTokens,token.toLowerCase())) {count++;}
        }
        return count;
    }
    public static double countWeakMatches(String p, String c) {
        double count = 0.0;
        String[] correctTokens = c.toLowerCase().split(" ");
        for (String token : p.split(" ")) {
            if (!hf.looksKnown(token))
                if (hf.contains(correctTokens,token.toLowerCase())) {count++;}
        }
        return count;
    }
}
