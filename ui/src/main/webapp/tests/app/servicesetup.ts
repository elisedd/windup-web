import {KeycloakService} from "../../app/services/keycloak.service";
jasmine.DEFAULT_TIMEOUT_INTERVAL = 50000;

KeycloakService.initWithKeycloakJSONPath("../keycloak.json");