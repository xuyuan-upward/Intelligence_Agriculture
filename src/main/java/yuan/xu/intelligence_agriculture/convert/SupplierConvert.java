package yuan.xu.intelligence_agriculture.convert;

import com.dsl.core.model.Supplier;
import com.dsl.pos.goods.resp.supplier.SupplierResp;

public class SupplierConvert {


    public static SupplierResp of(Supplier supplier) {
        SupplierResp resp = new SupplierResp();
        resp.setId(supplier.getId());
        resp.setSupplierName(supplier.getSupplierName());
        resp.setSupplierNo(supplier.getSupplierNo());
        resp.setStatus(supplier.getStatus());
        resp.setTenantId(supplier.getTenantId());
        resp.setLogicalNo(supplier.getLogicalNo());
        resp.setLicenseType(supplier.getLicenseType());
        resp.setPhone(supplier.getPhone());
        resp.setRegisterAddr(supplier.getRegisterAddr());
        resp.setLegalRepresentative(supplier.getLegalRepresentative());
        resp.setRegisterCapital(supplier.getRegisterCapital());
        resp.setCompanyCharge(supplier.getCompanyCharge());
        resp.setQualityManager(supplier.getQualityManager());
        resp.setEstablishDate(supplier.getEstablishDate());
        resp.setBusinessLimit(supplier.getBusinessLimit());
        resp.setDrugLicenseValidity(supplier.getDrugLicenseValidity());
        resp.setGspLicenseValidity(supplier.getGspLicenseValidity());
        resp.setWarehouseAddr(supplier.getWarehouseAddr());
        resp.setBusinessScope(supplier.getBusinessScope());
        resp.setPrincipal(supplier.getPrincipal());
        resp.setPrincipalValidity(supplier.getPrincipalValidity());
        resp.setBusinessLicenseNo(supplier.getBusinessLicenseNo());
        resp.setLicenseDate(supplier.getLicenseDate());
        resp.setGspLicenseNo(supplier.getGspLicenseNo());
        return resp;
    }
}
