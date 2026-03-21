package com.example.valueinsoftbackend.Service;


import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ProductFilter;
import com.example.valueinsoftbackend.Model.ResponseModel.ProductOperationResponse;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Model.Util.ProductUtilNames;
import com.example.valueinsoftbackend.util.PageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final DbPosProduct productRepository;
    private final DbPosProductCommandRepository productCommandRepository;

    @Autowired
    public ProductService(DbPosProduct productRepository, DbPosProductCommandRepository productCommandRepository) {
        this.productRepository = productRepository;
        this.productCommandRepository = productCommandRepository;
    }

    public ResponsePagination<Product> searchProductsByText(String[] words, String branchId, int companyId,
                                                            ProductFilter productFilter, PageHandler pageHandler) {
        ResponsePagination<Product> response = productRepository.getProductBySearchText(
                words, branchId, companyId, productFilter, pageHandler, true);

        if (productFilter == null && hasTokens(words) && response.getProducts().isEmpty()) {
            return productRepository.getProductBySearchText(words, branchId, companyId, null, pageHandler, false);
        }

        return response;
    }

    public ResponsePagination<Product> searchProductsByCompanyName(String companyName, String branchId, int companyId,
                                                                   ProductFilter productFilter, PageHandler pageHandler) {
        return productRepository.getProductBySearchCompanyName(companyName, branchId, companyId, productFilter, pageHandler);
    }

    public List<Product> getProductsAllRange(String branchId, int companyId, ProductFilter productFilter) {
        return productRepository.getProductsAllRange(branchId, companyId, productFilter);
    }

    public Product getProductById(int productId, int branchId, int companyId) {
        return productRepository.getProductById(productId, branchId, companyId);
    }

    public List<Product> getProductsByBarcode(String barcode, String branchId, int companyId) {
        return productRepository.getProductBySearchBarcode(barcode, branchId, companyId);
    }

    public List<ProductUtilNames> getProductNames(String text, int branchId, int companyId) {
        return productRepository.getProductNames(text, branchId, companyId);
    }

    public ProductOperationResponse addProduct(Product product, String branchId, int companyId) {
        long id = productCommandRepository.addProduct(product, branchId, companyId);
        return buildOperationResponse("The Product  Saved", id, product, "Add");
    }

    public ProductOperationResponse editProduct(Product product, String branchId, int companyId) {
        productCommandRepository.updateProduct(product, branchId, companyId);
        return buildOperationResponse("The Product Edit Saved", product.getProductId(), product, "Update");
    }

    private ProductOperationResponse buildOperationResponse(String title, long id, Product product, String transactionType) {
        return new ProductOperationResponse(
                title,
                id,
                product.getQuantity(),
                product.getBPrice() * product.getQuantity(),
                transactionType
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
