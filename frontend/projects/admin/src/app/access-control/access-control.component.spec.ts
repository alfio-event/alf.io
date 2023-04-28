import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccessControlComponent } from './access-control.component';

describe('AccessControlComponent', () => {
  let component: AccessControlComponent;
  let fixture: ComponentFixture<AccessControlComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AccessControlComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AccessControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
