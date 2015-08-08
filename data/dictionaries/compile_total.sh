rm total
cat abbreviations >> total
cat corporations >> total
cat places >> total
cat names >> total
cat twitter >> total
cat words >> total
cat probable >> total
sort total | uniq > t
mv t total
