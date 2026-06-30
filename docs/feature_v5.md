We need to add new feature related to the results.
For the play-off stage predictions are only for the 90 minute if the game is completed draw and extra time is added we should count only 90 minute result for users predictions. 

We may add configuration to allow full time result including penalties. 

Current problem is that football api overrides the result even if I updated it manually. 

Easies solution is to skip api data update if result inserted manually .

As a extended version we may store both 90 minute result and final result in db and based on configuration show 1st or 2nd approach. 

Anather extension can be provide ability to users to predict 90 minute result and in case if prediction is draw additional input equires who wins the play-off stage just Win not a exact score and 1 point for the correct prediction additianaly can be counted. 


so maximum point for play-off can be 3 point if exact score is predicted or draw + winner.

