$base = "http://localhost:8080/policies"
$h    = @{ "Content-Type" = "application/json" }
$today = [DateTime]::Today

function DateStr($daysOffset) {
    return $today.AddDays($daysOffset).ToString("yyyy-MM-dd")
}

# startDate is always 1 year before today
$start = $today.AddDays(-365).ToString("yyyy-MM-dd")

$policies = @(
    # --- Group 1: 30_DAY tier (end date 22-29 days from today) ---
    @{ policyNumber="POL-001"; holderName="Ramesh Kumar";   email="ramesh@example.com";    mobile="9876543210"; policyType="MOTOR";  premiumAmount=5000;  startDate=$start; endDate=DateStr(26) },
    @{ policyNumber="POL-002"; holderName="Aasha Patel";    email="aasha@example.com";     mobile="9123456780"; policyType="HEALTH"; premiumAmount=12000; startDate=$start; endDate=DateStr(28) },
    @{ policyNumber="POL-003"; holderName="Vijay Nair";     email="vijay@example.com";     mobile="9988776655"; policyType="TERM";   premiumAmount=8500;  startDate=$start; endDate=DateStr(22) },
    @{ policyNumber="POL-004"; holderName="Meena Krishnan";                                mobile="9444333222"; policyType="MOTOR";  premiumAmount=3200;  startDate=$start; endDate=DateStr(24) },

    # --- Group 2: 15_DAY tier (end date 8-14 days from today) ---
    @{ policyNumber="POL-005"; holderName="Karthik Raja";   email="karthik@example.com";   mobile="9000111222"; policyType="HEALTH"; premiumAmount=9500;  startDate=$start; endDate=DateStr(13) },
    @{ policyNumber="POL-006"; holderName="Saranya Devi";   email="saranya@example.com";   mobile="9111000333"; policyType="TERM";   premiumAmount=15000; startDate=$start; endDate=DateStr(11) },
    @{ policyNumber="POL-007"; holderName="Balu Sundaram";  email="balu@example.com";      mobile="9222111444"; policyType="MOTOR";  premiumAmount=4100;  startDate=$start; endDate=DateStr(9)  },
    @{ policyNumber="POL-008"; holderName="Deepa Mohan";                                   mobile="9333444555"; policyType="HEALTH"; premiumAmount=7800;  startDate=$start; endDate=DateStr(8)  },

    # --- Group 3: 7_DAY tier (end date 2-6 days from today) ---
    @{ policyNumber="POL-009"; holderName="Priya Menon";    email="priya@example.com";     mobile="9444555666"; policyType="TERM";   premiumAmount=20000; startDate=$start; endDate=DateStr(5)  },
    @{ policyNumber="POL-010"; holderName="Anbu Selvam";    email="anbu@example.com";      mobile="9555666777"; policyType="MOTOR";  premiumAmount=6200;  startDate=$start; endDate=DateStr(3)  },
    @{ policyNumber="POL-011"; holderName="Kavitha Rajan";  email="kavitha@example.com";   mobile="9666777888"; policyType="HEALTH"; premiumAmount=11000; startDate=$start; endDate=DateStr(2)  },

    # --- Group 4: OVERDUE tier (end date already past) ---
    @{ policyNumber="POL-012"; holderName="Suresh Babu";    email="suresh@example.com";    mobile="9777888999"; policyType="MOTOR";  premiumAmount=4800;  startDate=$start; endDate=DateStr(-3) },
    @{ policyNumber="POL-013"; holderName="Geetha Lakshmi"; email="geetha@example.com";    mobile="9888999000"; policyType="HEALTH"; premiumAmount=13500; startDate=$start; endDate=DateStr(-6) },
    @{ policyNumber="POL-014"; holderName="Murugan Raj";                                   mobile="9999000111"; policyType="TERM";   premiumAmount=25000; startDate=$start; endDate=DateStr(-8) },

    # --- Group 5: Will be RENEWED during demo ---
    @{ policyNumber="POL-015"; holderName="Lakshmi Priya";  email="lakshmi@example.com";   mobile="9101112131"; policyType="HEALTH"; premiumAmount=18000; startDate=$start; endDate=DateStr(6)  },
    @{ policyNumber="POL-016"; holderName="Dinesh Barath";  email="dinesh@example.com";    mobile="9141516171"; policyType="MOTOR";  premiumAmount=5500;  startDate=$start; endDate=DateStr(4)  },

    # --- Group 6: Will LAPSE (far past grace period) ---
    @{ policyNumber="POL-017"; holderName="Selvakumar T";   email="selva@example.com";     mobile="9181920212"; policyType="TERM";   premiumAmount=30000; startDate=$start; endDate=DateStr(-22) },
    @{ policyNumber="POL-018"; holderName="Revathi M";      email="revathi@example.com";   mobile="9222324252"; policyType="HEALTH"; premiumAmount=9000;  startDate=$start; endDate=DateStr(-27) },

    # --- Group 7: Far future - stays ACTIVE, skipped by Job 1 ---
    @{ policyNumber="POL-019"; holderName="Mani Iyer";      email="mani@example.com";      mobile="9262728293"; policyType="MOTOR";  premiumAmount=4400;  startDate=$start; endDate=DateStr(200) },
    @{ policyNumber="POL-020"; holderName="Santhiya R";     email="santhiya@example.com";  mobile="9303132333"; policyType="TERM";   premiumAmount=22000; startDate=$start; endDate=DateStr(365) }
)

Write-Host ""
Write-Host "Loading 20 demo policies relative to today: $($today.ToString('yyyy-MM-dd'))"
Write-Host "---------------------------------------------------"

$success = 0
$failed  = 0
foreach ($p in $policies) {
    $body = $p | ConvertTo-Json -Compress
    try {
        $r = Invoke-RestMethod -Uri $base -Method POST -Headers $h -Body $body
        Write-Host "OK  $($r.policyNumber) | $($r.policyType) | endDate: $($p.endDate) | $($r.status)"
        $success++
    } catch {
        Write-Host "ERR $($p.policyNumber) | $($_.Exception.Message)"
        $failed++
    }
}

Write-Host ""
Write-Host "Done: $success created, $failed failed."
Write-Host ""
Write-Host "Tier breakdown loaded:"
Write-Host "  30_DAY  : POL-001 to POL-004 (end in 22-28 days)"
Write-Host "  15_DAY  : POL-005 to POL-008 (end in  8-13 days)"
Write-Host "  7_DAY   : POL-009 to POL-011 (end in  2-5  days)"
Write-Host "  OVERDUE : POL-012 to POL-014 (end  3-8  days AGO)"
Write-Host "  RENEW   : POL-015, POL-016   (end in 4-6  days - will be renewed)"
Write-Host "  LAPSE   : POL-017, POL-018   (end 22-27 days AGO - will lapse)"
Write-Host "  ACTIVE  : POL-019, POL-020   (end 200-365 days away - stays ACTIVE)"