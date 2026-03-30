package com.example.valueinsoftbackend.Controller.posController;


import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ProductOperationResponse;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.ProductService;
import com.example.valueinsoftbackend.util.PageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@Validated
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final AuthorizationService authorizationService;

    @Autowired
    public ProductController(ProductService productService, AuthorizationService authorizationService) {
        this.productService = productService;
        this.authorizationService = authorizationService;
    }

    @RequestMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/{selectedPageNumber}", method = RequestMethod.GET)
    public Object getProducts(@PathVariable("companyId") @Positive int companyId,
                              @PathVariable("branchId") @Pattern(regexp = "\\d+", message = "branchId must be numeric") String branchId,
                              @PathVariable("searchType") String searchType,
                              @PathVariable("text") String text,
                              @PathVariable("selectedPageNumber") @Positive int selectedPageNumber,
                              Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                Integer.parseInt(branchId),
                "inventory.item.read"
        );
        log.info("getProducts called with searchType={}, companyId={}, branchId={}, text={}, page={}",
                searchType, companyId, branchId, text, selectedPageNumber);

        PageHandler pageHandler = new PageHandler("productId", selectedPageNumber, 10);

        switch (searchType) {
            case "dir":
                return productService.searchProductsByText(text.split("\\s+"), branchId, companyId, null, pageHandler);
            case "comName":
                return productService.searchProductsByCompanyName(text.trim(), branchId, companyId, null, pageHandler);
            case "Barcode":
                return productService.getProductsByBarcode(text.trim(), branchId, companyId);
            case "allData":
                throw new IllegalArgumentException("allData requires filter search endpoint");
            default:
                throw new RuntimeException("Search Type is not Correct");
        }
    }

    @PostMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/filter/{pageNumber}")
    public Object getProductsBySearchFilter(@PathVariable("companyId") @Positive int companyId,
                                            @PathVariable("branchId") @Pattern(regexp = "\\d+", message = "branchId must be numeric") String branchId,
                                            @PathVariable("searchType") String searchType,
                                            @PathVariable("text") String text,
                                            @PathVariable("pageNumber") @Positive int pageNumber,
                                            @Valid @RequestBody ProductFilter productFilter,
                                            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                Integer.parseInt(branchId),
                "inventory.item.read"
        );
        log.info("getProductsBySearchFilter called with searchType={}, companyId={}, branchId={}, text={}, page={}",
                searchType, companyId, branchId, text, pageNumber);

        PageHandler pageHandler = new PageHandler("productId", pageNumber, 10);

        switch (searchType) {
            case "dir":
                return productService.searchProductsByText(text.split("\\s+"), branchId, companyId, productFilter, pageHandler);
            case "comName":
                return productService.searchProductsByCompanyName(text.trim(), branchId, companyId, productFilter, pageHandler);
            case "shortCate":
                throw new IllegalArgumentException("shortCate search is not implemented");
            case "allData":
                return productService.getProductsAllRange(branchId, companyId, productFilter);
            default:
                throw new RuntimeException("Search Type is not Correct");
        }
    }

    @GetMapping("{companyId}/{branchId}/{productId}")
    public Product productById(@PathVariable("companyId") @Positive int companyId,
                               @PathVariable("branchId") @Positive int branchId,
                               @PathVariable("productId") @Positive int productId,
                               Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return productService.getProductById(productId, branchId, companyId);
    }

    @PostMapping("{companyId}/{branchId}/saveProduct")
    public ResponseEntity<ProductOperationResponse> newProduct(@Valid @RequestBody Product newProProduct,
                                                               @PathVariable @Pattern(regexp = "\\d+", message = "branchId must be numeric") String branchId,
                                                               @PathVariable @Positive int companyId,
                                                               Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                Integer.parseInt(branchId),
                "inventory.item.create"
        );
        ProductOperationResponse response = productService.addProduct(newProProduct, branchId, companyId);
        log.debug("Created product payload for company {} branch {}", companyId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("{companyId}/{branchId}/editProduct")
    public ResponseEntity<ProductOperationResponse> editProduct(@Valid @RequestBody Product editProduct,
                                                                @PathVariable @Pattern(regexp = "\\d+", message = "branchId must be numeric") String branchId,
                                                                @PathVariable @Positive int companyId,
                                                                Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                Integer.parseInt(branchId),
                "inventory.item.edit"
        );
        ProductOperationResponse response = productService.editProduct(editProduct, branchId, companyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/PN/{companyId}/{branchId}/{text}")
    public ResponseEntity<List<ProductUtilNames>> productNames(@PathVariable("companyId") @Positive int companyId,
                                                               @PathVariable("branchId") @Positive int branchId,
                                                               @PathVariable("text") String text,
                                                               Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return ResponseEntity.ok(productService.getProductNames(text, branchId, companyId));
    }
}
