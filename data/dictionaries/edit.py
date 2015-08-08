import sys

f = open(sys.argv[1],"r")

d = dict()

for line in f:
    entry = line.strip().split(":")
    word = entry[0]
    tran = entry[1]
    if word in d:
        #print word
        d[word]=d[word]+","+tran
    else:
        d[word]=tran

#print "_________________"
#print "_________________"
    
for key in d:
    print key+":"+d[key]

