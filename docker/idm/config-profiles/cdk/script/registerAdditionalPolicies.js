var additionalPolicies = require('additionalPolicy');

maximumArrayLength = additionalPolicies.maximumArrayLength;
enumValue = additionalPolicies.enumValue;
selfServicePasswordConfirm = additionalPolicies.selfServicePasswordConfirm;
addsPasswordComplexity = additionalPolicies.addsPasswordComplexity;

additionalPolicies.registerAdditionalPolicies(addPolicy);
