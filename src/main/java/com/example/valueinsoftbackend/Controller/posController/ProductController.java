package com.example.valueinsoftbackend.Controller.posController;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.util.PageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {

    DbPosProduct dbPosProduct;

    @Autowired
    public ProductController(DbPosProduct dbPosProduct) {
        this.dbPosProduct = dbPosProduct;
    }

    @RequestMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/{selectedPageNumber}", method = RequestMethod.GET)
    public Object getProducts(@PathVariable int companyId,
                              @PathVariable String branchId,
                              @PathVariable String searchType,
                              @PathVariable String text,
                                @PathVariable int selectedPageNumber
    ) {
        PageHandler pageHandler = new PageHandler("productId", selectedPageNumber, 10);
        // code here
        switch (searchType) {
            case "dir":
                log.info("getProducts: Search Type -> dir");

                String[] words = text.split("\\s+");

                return dbPosProduct.getProductBySearchText(words, branchId, companyId, null, pageHandler);
            case "comName":
                log.info("getProducts: Search Type -> ComName");
                return dbPosProduct.getProductBySearchCompanyName(text.trim(), branchId, companyId, null ,pageHandler);
            case "Barcode":
                log.info("getProducts: Search Type -> Barcode");

                return DbPosProduct.getProductBySearchBarcode(text.trim(), branchId, companyId, null);
            case "allData":
                log.info("getProducts: Search Type -> AllData");
                break;
        }
        throw new RuntimeException("Search Type is not Correct");
    }

    //todo -------- filter Search POST DAta
    @PostMapping(path = "/search/{searchType}/{companyId}/{branchId}/{text}/filter/{pageNumber}")
    public Object getProductsBySearchFilter(@PathVariable int companyId,
                                            @PathVariable String branchId,
                                            @PathVariable String searchType,
                                            @PathVariable String text,
                                            @PathVariable int pageNumber,
                                            @RequestBody ProductFilter productFilter) {
        // code here
        System.out.println(productFilter.toString());
        PageHandler pageHandler = new PageHandler("productId", pageNumber, 10);

        switch (searchType) {
            case "dir":
                log.info("getProductsBySearchFilter: Search Type -> dir");
                String[] words = text.split("\\s+");
                return dbPosProduct.getProductBySearchText(words, branchId, companyId, productFilter, pageHandler);
            case "comName":
                log.info("getProductsBySearchFilter: Search Type -> ComName");
                return dbPosProduct.getProductBySearchCompanyName(text.trim(), branchId, companyId, productFilter,pageHandler);
            case "shortCate":
                log.info("getProductsBySearchFilter: Search Type -> shortCate");

                break;
            case "allData":
                log.info("getProductsBySearchFilter: Search Type -> allData");
                return dbPosProduct.getProductsAllRange(branchId, companyId, productFilter);
        }
        throw new RuntimeException("Search Type is not Correct");
    }


    //----get----

    @GetMapping("{companyId}/{branchId}/{productId}")
    Product productById(@PathVariable int branchId, @PathVariable int companyId, @PathVariable int productId) {
        return dbPosProduct.getProductById(productId, branchId, companyId);
    }

    @PostMapping("{companyId}/{branchId}/saveProduct")
    ResponseEntity<Object> newProduct(@RequestBody Product newProProduct, @PathVariable String branchId, @PathVariable int companyId) {
        return dbPosProduct.AddProduct(newProProduct, branchId, companyId);
    }

    //--------------editProduct------------------
    @PutMapping("{companyId}/{branchId}/editProduct")
    ResponseEntity<Object> EditProduct(@RequestBody Product editProduct, @PathVariable String branchId, @PathVariable int companyId) {
        System.out.println("In Edit Product");
        return dbPosProduct.EditProduct(editProduct, branchId, companyId);
    }

    //--Search ProdsNames
    @GetMapping("/PN/{companyId}/{branchId}/{text}")
    ResponseEntity<Object> productNames(@PathVariable int branchId, @PathVariable int companyId, @PathVariable String text) {
        return dbPosProduct.getProductNames(text, branchId, companyId);
    }


}
