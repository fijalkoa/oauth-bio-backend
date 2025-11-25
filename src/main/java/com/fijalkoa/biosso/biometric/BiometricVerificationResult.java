package com.fijalkoa.biosso.biometric;

import lombok.Data;

@Data
public class BiometricVerificationResult {
    private boolean success;
    private double similarity;
}
