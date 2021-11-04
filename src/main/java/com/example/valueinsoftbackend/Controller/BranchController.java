package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Company;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/Branch")
@CrossOrigin("*")

public class BranchController {


    @RequestMapping(value = "/getBranchById", method = RequestMethod.GET)
    @ResponseBody
    public Company getCompanyById(


            @RequestParam("id") String id

    ) {


        return  DbCompany.getCompanyById( id);
    }


    @PostMapping("/AddBranch")

    public ResponseEntity<Object> newUser(@RequestBody Map<String,Object> body) {
        int branchId=-1;
        int companyId = (int) body.get("CompanyId");
        String branchName = body.get("branchName").toString();
        String branchLocation = body.get("branchLocation").toString();

        try {

             DbBranch.AddBranch(branchName,branchLocation,companyId);
            branchId= DbBranch.getBranchIdByCompanyNameAndBranchName(companyId,branchName);
        }catch (Exception e )
        {
            System.out.println(e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"title\" : \"the Branched added saved\", \"branchId\" : "+branchId+"}" );

    }



}
