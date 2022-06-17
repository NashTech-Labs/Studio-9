base.folder = ""

# Cortex accuracy computation
df <- read.csv(paste0(base.folder, "abalone_prediction"))
diff <- abs(df$LogRings - df$prediction_col)
mismatch <- sum(diff) / nrow(df)
accuracy <- 1.0 - mismatch
print(paste("Abalone cortex prediction mismatched prediction count =", sum(diff)))
print(paste("Abalone cortex prediction accuracy% =", round(accuracy * 100, 2)))


# Build logistic regression model
train.data <- read.csv(paste0(base.folder, "abalone_train.csv"))
holdout.data <- read.csv(paste0(base.folder, "abalone_holdout.csv"))
predictors <- c("Sex","Length","Diameter","Height","Whole_Weight","Shucked_Weight","Viscera_Weight","Shell_Weight")
response <- "LogRings"
fm <- formula(paste0(response, " ~ ", paste0(predictors, collapse = " + ")))
model <- glm(fm, family=binomial, train.data)
pred.probs <- predict(model, holdout.data, type = "response")
pred.labels <- ifelse(pred.probs >= 0.5, 1, 0)
diff.glm <- abs(holdout.data$LogRings - pred.labels)
accuracy.glm <- 1.0 - sum(diff.glm) / nrow(holdout.data)
print(paste("Abalone R glm prediction mismatched prediction count =", sum(diff.glm)))
print(paste("Abalone R glm prediction accuracy% =", round(accuracy.glm * 100, 2)))
