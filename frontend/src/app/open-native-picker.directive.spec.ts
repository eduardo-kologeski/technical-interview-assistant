import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { OpenNativePickerDirective } from './open-native-picker.directive';

@Component({
  imports: [OpenNativePickerDirective],
  template: '<input type="date"><input type="time"><input type="text">',
})
class PickerHostComponent {}

describe('OpenNativePickerDirective', () => {
  it('opens date and time pickers when the input body is clicked', () => {
    const fixture = TestBed.createComponent(PickerHostComponent);
    const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
    const datePicker = vi.fn();
    const timePicker = vi.fn();
    Object.defineProperty(inputs[0], 'showPicker', { value: datePicker });
    Object.defineProperty(inputs[1], 'showPicker', { value: timePicker });

    inputs[0].click();
    inputs[1].click();
    inputs[2].click();

    expect(datePicker).toHaveBeenCalledOnce();
    expect(timePicker).toHaveBeenCalledOnce();
  });
});
