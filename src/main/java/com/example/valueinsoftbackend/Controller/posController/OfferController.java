package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOffer;
import com.example.valueinsoftbackend.Model.Offer;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/Offer")
public class OfferController {

    private final DbPosOffer dbPosOffer;
    private final AuthorizationService authorizationService;

    @Autowired
    public OfferController(DbPosOffer dbPosOffer, AuthorizationService authorizationService) {
        this.dbPosOffer = dbPosOffer;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{branchId}/offers")
    public List<Offer> getOffers(@PathVariable @Positive int companyId,
                                 @PathVariable @Positive int branchId,
                                 Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.read"
        );
        return dbPosOffer.getOffers(companyId, branchId);
    }

    @PostMapping("/{companyId}/saveOffer")
    public ResponseEntity<Integer> saveOffer(@Valid @RequestBody Offer offer,
                                             @PathVariable @Positive int companyId,
                                             Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                offer.getBranchId(),
                "pos.sale.edit"
        );
        return ResponseEntity.status(201).body(dbPosOffer.saveOffer(offer, companyId));
    }

    @DeleteMapping("/{companyId}/{branchId}/deleteOffer/{offerId}")
    public ResponseEntity<String> deleteOffer(@PathVariable @Positive int companyId,
                                              @PathVariable @Positive int branchId,
                                              @PathVariable @Positive int offerId,
                                              Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.edit"
        );
        dbPosOffer.deleteOffer(offerId, branchId, companyId);
        return ResponseEntity.ok("Offer deleted successfully");
    }
}
