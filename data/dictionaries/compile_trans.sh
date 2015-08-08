rm translations
cat misspellings >> translations
cat twitter_acronyms >> translations
cat contractions >> translations
sort translations | uniq > t
mv t translations
python edit.py translations > t
mv t translations
