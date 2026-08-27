import { Directive, ElementRef, HostListener, inject } from '@angular/core';

@Directive({
  selector: 'input[type=date], input[type=time], input[type=datetime-local]',
})
export class OpenNativePickerDirective {
  private readonly input = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  @HostListener('pointerdown')
  openPicker() {
    if (this.input.disabled || this.input.readOnly || !this.input.showPicker) return;
    // The first mouse click focuses the field so the user can type immediately.
    // A subsequent click on the already focused field opens the native picker.
    if (document.activeElement !== this.input) return;
    try {
      this.input.showPicker();
    } catch {
      // Browsers without an available native picker keep their normal input behavior.
    }
  }
}
