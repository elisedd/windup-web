import {Component, Input, Output, ViewChild, EventEmitter} from "@angular/core";
import {ConfigurationOption} from "../model/configuration-option.model";
import {AdvancedOption} from "../generated/windup-services";
import {ModalDialogComponent} from "../shared/dialog/modal-dialog.component";
import {ConfigurationOptionsService} from "../configuration/configuration-options.service";
import {ValidationResult} from "../model/validation-result.model";
import {isString} from "util";

@Component({
    selector: 'wu-analysis-context-advanced-options',
    templateUrl: './analysis-context-advanced-options-modal.component.html'
})
export class AnalysisContextAdvancedOptionsModalComponent {
    @Input()
    configurationOptions: ConfigurationOption[] = [];

    @Input() @Output()
    selectedOptions: AdvancedOption[] = [];

    @Input()
    isReadOnly = false;

    @Output()
    advancedOptionsChanged: EventEmitter<AdvancedOption[]> = new EventEmitter<AdvancedOption[]>();

    newOption: AdvancedOption;
    private newOptionError: string;

    private get currentSelectedOptionDefinition(): ConfigurationOption {
        return this.configurationOptions.find((option:ConfigurationOption) => {
            return this.newOption.name == option.name;
        });
    }

    private get currentOptionType(): string {
        if (this.newOption.name == null)
            return null;

        let configurationOption = this.currentSelectedOptionDefinition;

        switch (configurationOption.type) {
            case "java.io.File":
            case "java.lang.String":
                return configurationOption.uitype == "SELECT_MANY" ? "select" : "text";
            case "java.lang.Boolean":
                return "checkbox";
            default:
                return null;
        }
    }

    constructor(private _configurationOptionsService:ConfigurationOptionsService) {
        this.resetCurrentOption();
    }

    private resetCurrentOption() {
        this.newOption = null;
    }

    get availableOptions(): ConfigurationOption[] {
        if (this.configurationOptions == null)
            return [];

        return this.configurationOptions.filter((option:ConfigurationOption) => {
            if (this.selectedOptions == null)
                return true;

            if (option.uitype == "MANY" || option.uitype == "SELECT_MANY")
                return true;

            return this.selectedOptions.find((selectedOption:AdvancedOption) => {
                        return selectedOption.name == option.name;
                   }) == null;
        }).sort((a: ConfigurationOption, b: ConfigurationOption) => {
            if (a.name < b.name) {
                return -1;
            } else if (a.name > b.name) {
                return 1;
            } else {
                return 0;
            }
        });
    }

    private removeAdvancedOption(index: number) {
        this.selectedOptions.splice(index, 1);
        this.advancedOptionsChanged.emit(this.selectedOptions);
        return false;
    }

    private startAddNew() {
        this.newOption = <AdvancedOption>{};
        this.newOption.value = "";
        return false;
    }

    private cancelAddAdvancedOption() {
        this.newOption = null;
        return false;
    }

    private addAdvancedOption(event: Event) {
        event.preventDefault();
        this.newOptionError = "";
        // Only accept null for a checkbox (with a checkbox "null" == false).
        if (this.currentOptionType != 'checkbox' && (this.newOption.value == null || this.newOption.value == "")) {
            this.newOptionError = "Value must be specified";
            return;
        }

        this._configurationOptionsService.validate(this.newOption)
            .subscribe((validationResult:ValidationResult) => {
                if (validationResult.level != "SUCCESS") {
                    // handle validation error
                    this.newOptionError = validationResult.message;
                    return;
                }

                if (this.selectedOptions == null)
                    this.selectedOptions = [];

                if (this.currentOptionType == "checkbox" && !this.newOption.value)
                    this.newOption.value = "false";

                this.selectedOptions.push(this.newOption);
                this.advancedOptionsChanged.emit(this.selectedOptions);

                this.resetCurrentOption();
            },
            (error: any) => {
                this.newOptionError = "Error validating option";
            });
    }

    newOptionTypeChanged() {
        if (this.newOption)
            this.newOption.value = "";
    }

    private sortOptionValues(optionValues: any[]): any[] {
        optionValues = optionValues.sort();
        // We should rather get rid of the reviewed tags in rules. WINDUPRULE-206.
        optionValues = optionValues.filter((v: any) => (!isString(v) || !(<string>v).startsWith("reviewed-")));
        // We also filter 'cloud-readiness' target because it can only be selected
        // through the "Cloud readiness analysis" check-box
        if (this.newOption.name === "target") {
            optionValues = optionValues.filter((v: any) => !((<string>v) === "cloud-readiness"));
        }
        return optionValues;
    }
}
