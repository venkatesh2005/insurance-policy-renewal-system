package com.fulcrumdigital.policyrenewal.batch.detection;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Carrier object passing both the policy and the calculated tier
// from the detection processor to the detection writer.
@Getter
@AllArgsConstructor
public class ReminderCandidate {
    private final Policy policy;
    private final String tier;
}
