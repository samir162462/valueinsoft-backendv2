package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ProductOperationResponse;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.util.PageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ProductService {

    private final DbPosProduct productRepository;
    private final DbPosProductCommandRepository productCommandRepository;
    private final LegacyInventoryBackfillService legacyInventoryBackfillService;

    @Autowired
    public ProductService(DbPosProduct productRepository,
                          DbPosProductCommandRepository productCommandRepository,
                          LegacyInventoryBackfillService legacyInventoryBackfillService) {
        this.productRepository = productRepository;
        this.productCommandRepository = productCommandRepository;
        this.legacyInventoryBackfillService = legacyInventoryBackfillService;
    }

    public ResponsePagination<Product> searchProductsByText(String[] words, String branchId, int companyId,
                                                            ProductFilter productFilter, PageHandler pageHandler) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, Integer.parseInt(branchId));
        ResponsePagination<Product> response = productRepository.getProductBySearchText(
                words, branchId, companyId, productFilter, pageHandler, true);

        if (productFilter == null && hasTokens(words) && response.getProducts().isEmpty()) {
            return productRepository.getProductBySearchText(words, branchId, companyId, null, pageHandler, false);
        }

        return response;
    }

    public ResponsePagination<Product> searchProductsByCompanyName(String companyName, String branchId, int companyId,
                                                                   ProductFilter productFilter, PageHandler pageHandler) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, Integer.parseInt(branchId));
        return productRepository.getProductBySearchCompanyName(companyName, branchId, companyId, productFilter, pageHandler);
    }

    public List<Product> getProductsAllRange(String branchId, int companyId, ProductFilter productFilter) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, Integer.parseInt(branchId));
        return productRepository.getProductsAllRange(branchId, companyId, productFilter);
    }

    public Product getProductById(int productId, int branchId, int companyId) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, branchId);
        Product product = productRepository.getProductById(productId, branchId, companyId);
        if (product != null) {
            return product;
        }
        Integer mappedProductId = legacyInventoryBackfillService.resolveModernProductId(companyId, branchId, productId);
        if (mappedProductId != null) {
            return productRepository.getProductById(mappedProductId, branchId, companyId);
        }
        return null;
    }

    public List<Product> getProductsByBarcode(String barcode, String branchId, int companyId) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, Integer.parseInt(branchId));
        return productRepository.getProductBySearchBarcode(barcode, branchId, companyId);
    }

    public List<ProductUtilNames> getProductNames(String text, int branchId, int companyId) {
        legacyInventoryBackfillService.backfillBranchProducts(companyId, branchId);
        return productRepository.getProductNames(text, branchId, companyId);
    }

    @Transactional
    public ProductOperationResponse addProduct(Product product, String branchId, int companyId) {
        if (product.getQuantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_QUANTITY_REQUIRED", "quantity must be greater than zero when creating a product");
        }

        long id = productCommandRepository.addProduct(product, branchId, companyId);
        Product savedProduct = productRepository.getProductById((int) id, Integer.parseInt(branchId), companyId);
        log.info("Created company-scoped product {} for company {} branch {}", id, companyId, branchId);
        return buildOperationResponse("The Product  Saved", id, savedProduct == null ? product : savedProduct, "Add");
    }

    @Transactional
    public ProductOperationResponse editProduct(Product product, String branchId, int companyId) {
        if (product.getProductId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_ID_REQUIRED", "productId must be greater than zero when editing a product");
        }

        legacyInventoryBackfillService.backfillBranchProducts(companyId, Integer.parseInt(branchId));
        Integer mappedProductId = legacyInventoryBackfillService.resolveModernProductId(companyId, Integer.parseInt(branchId), product.getProductId());
        if (mappedProductId != null) {
            product.setProductId(mappedProductId);
        }
        productCommandRepository.updateProduct(product, branchId, companyId);
        Product savedProduct = productRepository.getProductById(product.getProductId(), Integer.parseInt(branchId), companyId);
        log.info("Updated company-scoped product {} for company {} branch {}", product.getProductId(), companyId, branchId);
        return buildOperationResponse("The Product Edit Saved", product.getProductId(), savedProduct == null ? product : savedProduct, "Update");
    }

    private ProductOperationResponse buildOperationResponse(String title, long id, Product product, String transactionType) {
        return new ProductOperationResponse(
                title,
                id,
                product.getQuantity(),
                product.getBPrice() * product.getQuantity(),
                transactionType,
                product
        );
    }

    private boolean hasTokens(String[] words) {
        if (words == null) {
            return false;
        }

        for (String word : words) {
            if (word != null && !word.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
