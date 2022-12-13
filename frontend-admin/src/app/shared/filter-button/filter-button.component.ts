import { NgClass, NgIf } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { SvgIconComponent } from '@ngneat/svg-icon';

@Component({
  standalone: true,
  imports: [SvgIconComponent, NgClass, NgIf],
  selector: 'app-filter-button',
  template: `
  <button
    type="button"
    class="btn"
    style="border-radius: 20px"
    [ngClass]="{'btn-outline-primary': !checked, 'btn-primary': checked}"
    (click)="onClick()"
  >
    <svg-icon key="check" size="md" *ngIf="checked"></svg-icon> {{text}}
  </button>`,
})
export class FilterButtonComponent {

  @Input()
  public text?: string;

  @Input()
  public checked: boolean = false;

  @Output()
  public toggleFilter = new EventEmitter<boolean>();

  constructor() { }

  public onClick(): void {
    this.toggleFilter.emit(!this.checked);
  }
}
