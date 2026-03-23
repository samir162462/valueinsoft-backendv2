package com.example.valueinsoftbackend.Controller.posController;


import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ProductOperationResponse;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.Service.ProductService;
import com.example.valueinsoftbackend.util.PageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @RequestMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/{selectedPageNumber}", method = RequestMethod.GET)
    public Object getProducts(@PathVariable("companyId") int companyId,
                              @PathVariable("branchId") String branchId,
                              @PathVariable("searchType") String searchType,
                              @PathVariable("text") String text,
                              @PathVariable("selectedPageNumber") int selectedPageNumber) {
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
    public Object getProductsBySearchFilter(@PathVariable("companyId") int companyId,
                                            @PathVariable("branchId") String branchId,
                                            @PathVariable("searchType") String searchType,
                                            @PathVariable("text") String text,
                                            @PathVariable("pageNumber") int pageNumber,
                                            @RequestBody ProductFilter productFilter) {
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
    public Product productById(@PathVariable("companyId") int companyId,
                               @PathVariable("branchId") int branchId,
                               @PathVariable("productId") int productId) {
        return productService.getProductById(productId, branchId, companyId);
    }

    @PostMapping("{companyId}/{branchId}/saveProduct")
    public ResponseEntity<ProductOperationResponse> newProduct(@RequestBody Product newProProduct,
                                                               @PathVariable String branchId,
                                                               @PathVariable int companyId) {
        ProductOperationResponse response = productService.addProduct(newProProduct, branchId, companyId);
        System.out.println(newProProduct);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("{companyId}/{branchId}/editProduct")
    public ResponseEntity<ProductOperationResponse> editProduct(@RequestBody Product editProduct,
                                                                @PathVariable String branchId,
                                                                @PathVariable int companyId) {
        ProductOperationResponse response = productService.editProduct(editProduct, branchId, companyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/PN/{companyId}/{branchId}/{text}")
    public ResponseEntity<List<ProductUtilNames>> productNames(@PathVariable("companyId") int companyId,
                                                               @PathVariable("branchId") int branchId,
                                                               @PathVariable("text") String text) {
        return ResponseEntity.ok(productService.getProductNames(text, branchId, companyId));
    }
}
