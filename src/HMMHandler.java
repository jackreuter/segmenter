import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
import cc.mallet.fst.SimpleTagger.*;
import cc.mallet.fst.confidence.*;

//uses mallet libraries to handle HMMs for segmentation scoring and unknown word scoring
//may be sloppy/hacky due to lack of full knowledge of mallet
//hmm1 and hmm2 are separate so that hmm2 can vary in order
//scoring functions right now write to a file and then read it in, because that was how I 
//figured out to make it work, but this is slow and should be changed
public class HMMHandler {

    private HMM hmm1; //used for edge weights
    private HMM hmm2; //used for POS tagging
    private HMM charhmm; //used for unknown word scoring
    private Multinomial initials1; //pi values for hmm1
    private Multinomial charInitials; //pi values for charhmm, essentially just a markov chain
    private Pipe pipe; //mallet input method
    private HelperFunctions hf;

    //constructor for just hmm1 and hmm2, charhmm file is hardcoded
    public HMMHandler(String hmm1File, String hmm2File) throws Exception {
        ObjectInputStream s1 = new ObjectInputStream(new FileInputStream(hmm1File));
        hmm1 = (HMM) s1.readObject();
        initials1 = (Multinomial) s1.readObject();
        s1.close();

        ObjectInputStream s2 = new ObjectInputStream(new FileInputStream(hmm2File));
        hmm2 = (HMM) s2.readObject();
        s2.close();

        ObjectInputStream cha = new ObjectInputStream(new FileInputStream("../data/hmms/charhmm"));
        charhmm = (HMM) cha.readObject();
        charInitials = (Multinomial) cha.readObject();
        cha.close();

        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new SimpleTaggerSentence2TokenSequence());
        pipes.add(new TokenSequence2FeatureSequence());
        pipe = new SerialPipes(pipes);

        hf = new HelperFunctions();
    }

    //if only edge weights are needed, used for AH scoring
    public HMMHandler(String hmmFile) throws Exception {
        ObjectInputStream s1 = new ObjectInputStream(new FileInputStream(hmmFile));
        hmm1 = (HMM) s1.readObject();
        initials1 = (Multinomial) s1.readObject();
        s1.close();

        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new SimpleTaggerSentence2TokenSequence());
        pipes.add(new TokenSequence2FeatureSequence());
        pipe = new SerialPipes(pipes);

        hf = new HelperFunctions();
    }

    //gets HH score = average edge weight * tagging confidence
    public double scoreSeg(String s) throws Exception {
        hf.writeCurrentSeg(s);
        InstanceList seg = new InstanceList(pipe);
        seg.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(new FileInputStream("current/seg"))), Pattern.compile("^\\s*$"), true));

        Sequence input = (Sequence) seg.get(0).getData();
        Sequence output = (apply(hmm2,input,1))[0];

        ViterbiConfidenceEstimator ce = new ViterbiConfidenceEstimator(hmm2);
        double confidence = ce.estimateConfidenceFor(seg.get(0), new String[]{}, new String[]{})*100;
        double score = getStateProb(output,hmm1,initials1)*confidence;
        return score;
    }

    //scores unknown word for probability of being real
    //just gets average edge weight in markov chain
    public double scoreWord(String s) throws Exception {
        hf.writeCurrentWord(s.toUpperCase());
        InstanceList word = new InstanceList(pipe);
        word.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(new FileInputStream("current/word"))), Pattern.compile("^\\s*$"), true));

        Sequence input = (Sequence) word.get(0).getData();
        Sequence output = (apply(charhmm,input,1))[0];

        ViterbiConfidenceEstimator ce = new ViterbiConfidenceEstimator(charhmm);
        double confidence = ce.estimateConfidenceFor(word.get(0), new String[]{}, new String[]{})*100;
        double score = getStateProb(output,charhmm,charInitials)*confidence;
        return score;
    }

    //gets average edge weight
    public double getStateProb(String s) throws Exception  {
        hf.writeCurrentLabels(s);
        InstanceList labels = new InstanceList(pipe);        
        labels.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(new FileInputStream("current/labels"))), Pattern.compile("^\\s*$"), true));
        Sequence output = (Sequence) labels.get(0).getData();
        return getStateProb(output,hmm1,initials1);
    }
    
    public double getStateProb(Sequence output, HMM hmm, Multinomial initials) {
        Alphabet alphabet = hmm.getOutputAlphabet();
        double probsum = 0.0;
        for (int i=0; i<output.size(); i++) {
            Object state = output.get(i);
            double transprob;
            if (i==0) {
                transprob = initials.probability(state);
            } else {
                Object prev = output.get(i-1);
                int index = alphabet.lookupIndex(prev);
                Multinomial[] transitions = hmm.getTransitionMultinomial();
                transprob = transitions[index].probability(state);
            }
            probsum += transprob;
        }
        return probsum/output.size();
    }

    /**
       taken from mallet's SIMPLETAGGER
       * Apply a transducer to an input sequence to produce the k highest-scoring
       * output sequences.
       *
       * @param model the <code>Transducer</code>
       * @param input the input sequence
       * @param k the number of answers to return
       * @return array of the k highest-scoring output sequences
       */
    public Sequence[] apply(Transducer model, Sequence input, int k)
    {
        Sequence[] answers;
        if (k == 1) {
            answers = new Sequence[1];
            answers[0] = model.transduce (input);
        }
        else {
            MaxLatticeDefault lattice =
                new MaxLatticeDefault (model, input, null, 100000);

            answers = lattice.bestOutputSequences(k).toArray(new Sequence[0]);
        }
        return answers;
    }
    
}
