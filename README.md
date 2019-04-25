# Conformative-Filtering
Implementation of [*Conformative filtering for Implicit Feedback Data*](https://arxiv.org/pdf/1704.01889.pdf). F. Khawar, N. L. Zhang ECIR 2019

To replicate the results on the Movielens20M dataset

-Learn the model using: 
```
java -Xmx32764M -cp COF.jar ForestLTM PATHTOFILE/user-item-time-train70andVal15 10 3 0.01 3 NEWMovilens20m-train70AndVal15-ForestLTM 5 10 500 1 25 all 10 3
```

Please see model/FastHLTA/ForestLTM.java for details of the input parameters. However, for most purpose you will just need to provide the training data.

This would output the model Movilens20m-train70AndVal15-ForestLTM.bif. We we also provide the pretrained model incase you want to skip the model building. We use a fast variant of HLTA with restricted clique tree propagation. Please see our paper [*Learning Hierarchical Item Categories from Implicit Feedback Data for Efficient Recommendations and Browsing*](https://arxiv.org/pdf/1806.02056.pdf) for details.

Once the model .bif file is availabe we can use it for recommendation.

-Make recommendations and evaluate
```
java -Xmx32096M -cp COF.jar ItemRecommendation --overlap-items --training-file=PATHTOFILE/user-item-time-train70andVal15 --test-   file=PATHTOFILE/user-item-time-test15 --recommender=LTM_FR --recommender-options="modelPath=PATHTOFILE/Movilens20m-train70AndVal15-ForestLTM.bif latentLevel=1 historySize=5 timeInGroupPreference=true timeRestrictedTrainfileName=PATHTOFILE/user-item-time-train70andVal15" --test-users=PATHTOFILE/user-item-time-train70andVal15-users
```
