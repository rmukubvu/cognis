package io.cognis.vertical.sa.agriculture.tool;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Guides emerging farmers through South African government agricultural support programmes.
 *
 * <p>Covers:
 * <ul>
 *   <li>CASP — Comprehensive Agricultural Support Programme (DAFF)</li>
 *   <li>RECAP — Recapitalisation and Development Programme</li>
 *   <li>Ilima/Letsema — smallholder food security programme</li>
 *   <li>Land Bank Emerging Farmer Finance</li>
 *   <li>AgriSETA — agricultural skills and training</li>
 * </ul>
 */
public final class SubsidyNavigatorTool implements Tool {

    private static final List<Programme> PROGRAMMES = List.of(
        new Programme(
            "casp",
            "CASP — Comprehensive Agricultural Support Programme",
            "Post-settlement support for land reform beneficiaries and small-scale farmers. " +
            "Covers infrastructure (irrigation, fencing, storage), production inputs, and training.",
            List.of("limpopo", "mpumalanga", "kwazulu-natal", "free-state", "north-west",
                    "northern-cape", "western-cape", "eastern-cape", "gauteng"),
            0.5,
            200.0,
            "Apply at your nearest DAFF provincial office. Required: ID, proof of land ownership or lease, farm business plan.",
            "DAFF National: +27 12 319 7000 | www.dalrrd.gov.za"
        ),
        new Programme(
            "recap",
            "RECAP — Recapitalisation and Development Programme",
            "Supports distressed farms acquired under land reform. Provides mentorship, " +
            "capital investment, and enterprise development partnerships.",
            List.of("limpopo", "mpumalanga", "kwazulu-natal", "eastern-cape", "north-west"),
            1.0,
            500.0,
            "Apply through DALRRD regional offices. Requires proof of land reform acquisition and operational plan.",
            "DALRRD: +27 12 319 7000 | landreform@dalrrd.gov.za"
        ),
        new Programme(
            "ilima",
            "Ilima/Letsema — Food Security Programme",
            "Targeted at subsistence and smallholder farmers. Provides seeds, fertiliser, " +
            "mechanisation support, and training. Priority: food-insecure households.",
            List.of("limpopo", "kwazulu-natal", "eastern-cape", "mpumalanga", "north-west"),
            0.0,
            20.0,
            "Apply through your local municipality or DAFF extension officer. No minimum farm size.",
            "Provincial DAFF offices | Toll-free: 0800 200 400"
        ),
        new Programme(
            "land_bank",
            "Land Bank — Emerging Farmer Finance",
            "Development loans for emerging farmers at preferential interest rates. " +
            "Products: production loans, asset finance (equipment, irrigation), land acquisition loans.",
            List.of("limpopo", "mpumalanga", "kwazulu-natal", "free-state", "north-west",
                    "northern-cape", "western-cape", "eastern-cape", "gauteng"),
            1.0,
            1000.0,
            "Apply online or at a Land Bank branch. Required: 3-year business plan, financial statements, proof of land.",
            "Land Bank: +27 12 686 0500 | www.landbank.co.za"
        ),
        new Programme(
            "agriseta",
            "AgriSETA — Agricultural Skills Development",
            "Free training and skills programmes for farmers: crop production, animal husbandry, " +
            "agribusiness management. Includes learnerships and mentorship programmes.",
            List.of("limpopo", "mpumalanga", "kwazulu-natal", "free-state", "north-west",
                    "northern-cape", "western-cape", "eastern-cape", "gauteng"),
            0.0,
            9999.0,
            "Register on the AgriSETA portal or contact your provincial office.",
            "AgriSETA: +27 12 301 5600 | www.agriseta.co.za"
        )
    );

    @Override
    public String name() {
        return "subsidy_navigator";
    }

    @Override
    public String description() {
        return "Navigate SA government agricultural support programmes (CASP, RECAP, Ilima/Letsema, Land Bank, AgriSETA). " +
               "Actions: list_programmes, check_eligibility, get_programme_details.";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("list_programmes", "check_eligibility", "get_programme_details")
                ),
                "province", Map.of(
                    "type", "string",
                    "description", "SA province (limpopo, kwazulu-natal, western-cape, etc.)"
                ),
                "hectares", Map.of(
                    "type", "number",
                    "description", "Farm size in hectares"
                ),
                "programme_id", Map.of(
                    "type", "string",
                    "description", "Programme ID: casp, recap, ilima, land_bank, agriseta"
                )
            ),
            "required", List.of("action"),
            "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        String action      = (String) input.get("action");
        String province    = ((String) input.getOrDefault("province", "")).toLowerCase().trim();
        double hectares    = input.get("hectares") instanceof Number n ? n.doubleValue() : -1.0;
        String programmeId = (String) input.getOrDefault("programme_id", "");

        return switch (action) {
            case "list_programmes"      -> listProgrammes(province);
            case "check_eligibility"    -> checkEligibility(province, hectares);
            case "get_programme_details" -> getProgrammeDetails(programmeId);
            default -> "Unknown action: " + action;
        };
    }

    private String listProgrammes(String province) {
        List<Programme> relevant = province.isBlank()
            ? PROGRAMMES
            : PROGRAMMES.stream().filter(p -> p.provinces().contains(province)).toList();

        if (relevant.isEmpty()) {
            return "No programmes found for province '" + province + "'. Check the spelling or try without a province filter.";
        }

        StringBuilder sb = new StringBuilder("Available support programmes");
        if (!province.isBlank()) {
            sb.append(" in ").append(province);
        }
        sb.append(":\n\n");
        relevant.forEach(p -> sb.append("  [%s] %s%n    %s%n%n".formatted(
            p.id().toUpperCase(), p.name(), p.description())));
        sb.append("Use get_programme_details with programme_id for full application info.");
        return sb.toString().trim();
    }

    private String checkEligibility(String province, double hectares) {
        if (province.isBlank()) {
            return "Please provide your province to check eligibility.";
        }
        List<Programme> eligible = PROGRAMMES.stream()
            .filter(p -> p.provinces().contains(province))
            .filter(p -> hectares < 0 || (hectares >= p.minHectares() && hectares <= p.maxHectares()))
            .toList();

        if (eligible.isEmpty()) {
            return "No programmes matched for province=" + province + ", hectares=" + hectares +
                   ". Contact DAFF at +27 12 319 7000 for personalised advice.";
        }

        StringBuilder sb = new StringBuilder("You may qualify for %d programme(s):\n\n".formatted(eligible.size()));
        eligible.forEach(p -> sb.append("  ✓ [%s] %s%n    Contact: %s%n%n".formatted(
            p.id().toUpperCase(), p.name(), p.contact())));
        return sb.toString().trim();
    }

    private String getProgrammeDetails(String programmeId) {
        if (programmeId.isBlank()) {
            return "Provide a programme_id: " + PROGRAMMES.stream().map(Programme::id).toList();
        }
        return PROGRAMMES.stream()
            .filter(p -> p.id().equalsIgnoreCase(programmeId))
            .findFirst()
            .map(p -> """
                %s
                %s
                Eligible provinces: %s
                Farm size: %.1f – %.0f ha (0 = no minimum)
                How to apply: %s
                Contact: %s
                """.formatted(
                    p.name(), p.description(),
                    String.join(", ", p.provinces()),
                    p.minHectares(), p.maxHectares(),
                    p.howToApply(), p.contact()
                ).trim())
            .orElse("Programme '" + programmeId + "' not found. Available: " +
                    PROGRAMMES.stream().map(Programme::id).toList());
    }

    private record Programme(
        String id,
        String name,
        String description,
        List<String> provinces,
        double minHectares,
        double maxHectares,
        String howToApply,
        String contact
    ) {}
}
