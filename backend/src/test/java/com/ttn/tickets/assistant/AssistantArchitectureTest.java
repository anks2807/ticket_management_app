package com.ttn.tickets.assistant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

class AssistantArchitectureTest {

    @Test
    void assistantPackageDoesNotDependOnTicketCommandWrites() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ttn.tickets.assistant");
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.ttn.tickets.assistant..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.ttn.tickets.application..");
        rule.check(classes);
    }
}
