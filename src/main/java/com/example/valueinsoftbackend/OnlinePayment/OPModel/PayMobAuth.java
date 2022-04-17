/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.OnlinePayment.OPModel;

import java.io.Serializable;

public  class PayMobAuth implements Serializable {

     String token ;

     public PayMobAuth(String token) {
          this.token = token;
     }

     public String getToken() {
          return token;
     }

     public void setToken(String token) {
          this.token = token;
     }
}
