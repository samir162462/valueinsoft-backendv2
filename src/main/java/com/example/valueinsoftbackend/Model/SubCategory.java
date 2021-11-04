package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class SubCategory {
    int sCId;
    ArrayList<String> names;
    int categoryId;

    public SubCategory(int sCId, ArrayList<String> names, int categoryId) {
        this.sCId = sCId;
        this.names = names;
        this.categoryId = categoryId;
    }

    public int getsCId() {
        return sCId;
    }

    public void setsCId(int sCId) {
        this.sCId = sCId;
    }

    public ArrayList<String> getNames() {
        return names;
    }

    public void setNames(ArrayList<String> names) {
        this.names = names;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public String toString() {
        return "SubCategory{" +
                "sCId=" + sCId +
                ", names=" + names +
                ", categoryId=" + categoryId +
                '}';
    }
}
