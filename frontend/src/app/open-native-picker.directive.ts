import { Directive, ElementRef, HostListener, inject } from '@angular/core';

@Directive({
  selector: 'input[type=date], input[type=time], input[type=datetime-local]',
})
export class OpenNativePickerDirective {
  private readonly input = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  @HostListener('pointerdown')
  openPicker() {
    if (this.input.disabled || this.input.readOnly || !this.input.showPicker) return;
    try {
      this.input.showPicker();
    } catch {
      // Browsers without an available native picker keep their normal input behavior.
    }
  }
}
