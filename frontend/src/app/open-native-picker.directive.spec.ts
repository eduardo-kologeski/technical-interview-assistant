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

    inputs[0].dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    inputs[1].dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    inputs[2].dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));

    expect(datePicker).toHaveBeenCalledOnce();
    expect(timePicker).toHaveBeenCalledOnce();
  });

  it('keeps keyboard input available without opening the picker', () => {
    const fixture = TestBed.createComponent(PickerHostComponent);
    const input = fixture.nativeElement.querySelector('input[type="date"]') as HTMLInputElement;
    const picker = vi.fn();
    Object.defineProperty(input, 'showPicker', { value: picker });

    input.focus();
    input.value = '2026-09-01';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: '1', bubbles: true }));

    expect(input.value).toBe('2026-09-01');
    expect(picker).not.toHaveBeenCalled();
  });

  it('opens on the first mouse click and still allows typing', () => {
    const fixture = TestBed.createComponent(PickerHostComponent);
    const input = fixture.nativeElement.querySelector('input[type="time"]') as HTMLInputElement;
    const picker = vi.fn();
    Object.defineProperty(input, 'showPicker', { value: picker });

    input.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    input.focus();
    input.value = '15:30';
    input.dispatchEvent(new Event('input', { bubbles: true }));

    expect(input.value).toBe('15:30');
    expect(picker).toHaveBeenCalledOnce();
  });
});
