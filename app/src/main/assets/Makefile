CPU=attiny84

BOOTSYM = DEVL$(ID)

all: blink.hex

%.elf: %.S
	avr-gcc -mmcu=attiny84 -Os -Xassembler --gdwarf-2 -nostartfiles -nodefaultlibs -nostdlib -o $@ $<
	avr-objdump -d $@ >$@.dump

%.hex: %.elf
	avr-objcopy -O ihex $< $@

android: blink.hex
	cat blink.hex | ncat 192.168.15.139 12345

clean:
	rm -f *.elf *.hex
