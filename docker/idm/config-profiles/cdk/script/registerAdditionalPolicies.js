var additionalPolicies = require('additionalPolicy');

maximumArrayLength = additionalPolicies.maximumArrayLength;
enumValue = additionalPolicies.enumValue;
addsPasswordComplexity = additionalPolicies.addsPasswordComplexity;

additionalPolicies.registerAdditionalPolicies(addPolicy);
