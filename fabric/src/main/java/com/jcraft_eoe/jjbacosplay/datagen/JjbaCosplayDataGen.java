package com.jcraft_eoe.jjbacosplay.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

// Stub apenas para desenvolvimento: evita ClassNotFoundException no datagen
// (algum entrypoint "fabric-datagen" de uma dependência aponta para esta classe, que não existe no jar).
// Esta classe é excluída do jar final em fabric/build.gradle (shadowJar).
public class JjbaCosplayDataGen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
    }
}
