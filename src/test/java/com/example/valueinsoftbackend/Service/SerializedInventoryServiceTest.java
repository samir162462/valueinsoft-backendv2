package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductTrackingRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductUnitRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.ProductTrackingMetadata;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitStockInRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitTransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SerializedInventoryServiceTest {

    private DbInventoryProductTrackingRepository productTrackingRepository;
    private DbInventoryProductUnitRepository productUnitRepository;
    private DbInventoryStockMovementRepository stockMovementRepository;
    private SerializedInventoryService service;

    @BeforeEach
    void setUp() {
        productTrackingRepository = Mockito.mock(DbInventoryProductTrackingRepository.class);
        productUnitRepository = Mockito.mock(DbInventoryProductUnitRepository.class);
        stockMovementRepository = Mockito.mock(DbInventoryStockMovementRepository.class);
        service = new SerializedInventoryService(productTrackingRepository, productUnitRepository, stockMovementRepository);
    }

    @Test
    void stockInSerializedUnitsRejectsDuplicateIdentifiersInRequest() {
        when(productTrackingRepository.findTrackingMetadata(7, 41)).thenReturn(Optional.of(
                new ProductTrackingMetadata(TrackingType.IMEI, null, null, 0L)
        ));

        SerializedUnitStockInRequest request = new SerializedUnitStockInRequest(
                7,
                3,
                41,
                TrackingType.IMEI,
                88L,
                "PURCHASE",
                "P-1",
                null,
                "cashier",
                "idem-1",
                List.of(
                        new SerializedUnitInput(null, "356789123456789", null, "NEW"),
                        new SerializedUnitInput(null, "356789123456789", null, "NEW")
                )
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.stockInSerializedUnits(request));

        assertEquals("SERIALIZED_UNIT_DUPLICATE_IN_REQUEST", exception.getCode());
        verify(productUnitRepository, never()).insertProductUnit(any());
        verify(stockMovementRepository, never()).insertMovement(any());
    }

    @Test
    void markSerializedUnitSoldUsesGuardedUpdateAndWritesMovement() {
        ProductUnit unit = unit(7001, 7, 3, 41, ProductUnitStatus.AVAILABLE);
        when(productUnitRepository.findAvailableForSaleForUpdate(7, 3, 41, 7001)).thenReturn(Optional.of(unit));
        when(productUnitRepository.markSold(7, 3, 41, 7001, 9001, 8001L, 500L)).thenReturn(1);
        when(stockMovementRepository.insertMovement(any())).thenReturn(10001L);

        service.markSerializedUnitSold(7, 3, 41, 7001, 9001, 8001L, 500L, "cashier");

        verify(productUnitRepository).markSold(7, 3, 41, 7001, 9001, 8001L, 500L);

        ArgumentCaptor<InventoryStockMovement> movementCaptor = ArgumentCaptor.forClass(InventoryStockMovement.class);
        verify(stockMovementRepository).insertMovement(movementCaptor.capture());
        InventoryStockMovement movement = movementCaptor.getValue();
        assertEquals(7, movement.getCompanyId());
        assertEquals(3L, movement.getBranchId());
        assertEquals(41, movement.getProductId());
        assertEquals(7001L, movement.getProductUnitId());
        assertEquals(InventoryMovementType.SALE, movement.getMovementType());
        assertEquals(0, BigDecimal.ONE.negate().compareTo(movement.getQuantityDelta()));
        assertEquals("ORDER", movement.getReferenceType());
        assertEquals("9001", movement.getReferenceId());
        assertEquals(8001L, movement.getReferenceLineId());
        assertEquals(500L, movement.getCustomerId());
    }

    @Test
    void transferSerializedUnitsRejectsDuplicateUnitIds() {
        SerializedUnitTransferRequest request = new SerializedUnitTransferRequest(
                7,
                3,
                4,
                41,
                List.of(7001L, 7001L),
                "admin",
                "transfer-1"
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.transferSerializedUnits(request));

        assertEquals("SERIALIZED_UNIT_DUPLICATE_IN_TRANSFER", exception.getCode());
    }

    @Test
    void transferSerializedUnitsMovesAvailableUnitAndWritesOutAndInMovements() {
        ProductUnit unit = unit(7001, 7, 3, 41, ProductUnitStatus.AVAILABLE);
        when(productUnitRepository.findAvailableForSaleForUpdate(7, 3, 41, 7001)).thenReturn(Optional.of(unit));
        when(productUnitRepository.updateStatus(
                7,
                3,
                7001,
                ProductUnitStatus.AVAILABLE,
                ProductUnitStatus.AVAILABLE,
                4L
        )).thenReturn(1);
        when(stockMovementRepository.insertMovement(any())).thenReturn(10001L);

        SerializedUnitTransferRequest request = new SerializedUnitTransferRequest(
                7,
                3,
                4,
                41,
                List.of(7001L),
                "admin",
                "transfer-1"
        );

        service.transferSerializedUnits(request);

        verify(productUnitRepository).updateStatus(
                7,
                3,
                7001,
                ProductUnitStatus.AVAILABLE,
                ProductUnitStatus.AVAILABLE,
                4L
        );

        ArgumentCaptor<InventoryStockMovement> movementCaptor = ArgumentCaptor.forClass(InventoryStockMovement.class);
        verify(stockMovementRepository, times(2)).insertMovement(movementCaptor.capture());
        List<InventoryStockMovement> movements = movementCaptor.getAllValues();

        InventoryStockMovement transferOut = movements.get(0);
        assertEquals(InventoryMovementType.TRANSFER_OUT, transferOut.getMovementType());
        assertEquals(3L, transferOut.getBranchId());
        assertEquals(3L, transferOut.getFromBranchId());
        assertEquals(4L, transferOut.getToBranchId());
        assertEquals(0, BigDecimal.ONE.negate().compareTo(transferOut.getQuantityDelta()));
        assertEquals("transfer-1:356789123456789:out", transferOut.getIdempotencyKey());

        InventoryStockMovement transferIn = movements.get(1);
        assertEquals(InventoryMovementType.TRANSFER_IN, transferIn.getMovementType());
        assertEquals(4L, transferIn.getBranchId());
        assertEquals(3L, transferIn.getFromBranchId());
        assertEquals(4L, transferIn.getToBranchId());
        assertEquals(0, BigDecimal.ONE.compareTo(transferIn.getQuantityDelta()));
        assertEquals("transfer-1:356789123456789:in", transferIn.getIdempotencyKey());
    }

    private ProductUnit unit(long productUnitId, long companyId, long branchId, long productId, ProductUnitStatus status) {
        ProductUnit unit = new ProductUnit();
        unit.setProductUnitId(productUnitId);
        unit.setCompanyId(companyId);
        unit.setBranchId(branchId);
        unit.setProductId(productId);
        unit.setTrackingType(TrackingType.IMEI);
        unit.setUnitIdentifier("356789123456789");
        unit.setImei("356789123456789");
        unit.setStatus(status);
        unit.setConditionCode("NEW");
        return unit;
    }
}
