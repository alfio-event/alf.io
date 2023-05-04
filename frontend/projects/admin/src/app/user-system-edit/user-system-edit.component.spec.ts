import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserSystemEditComponent } from './user-system-edit.component';

describe('UserSystemEditComponent', () => {
  let component: UserSystemEditComponent;
  let fixture: ComponentFixture<UserSystemEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ UserSystemEditComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserSystemEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
