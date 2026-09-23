with open('src/main/java/com/printops/demo/controller/PrinterController.java', 'r') as f:
    lines = f.readlines()

# find the last return statement
last_return_idx = -1
for i, line in enumerate(lines):
    if 'return ResponseEntity.ok(printerService.getPrintersWithDueMaintenance(LocalDate.now()));' in line:
        last_return_idx = i

if last_return_idx != -1:
    lines = lines[:last_return_idx+1]
    lines.append('    }\n')
    lines.append('}\n')

with open('src/main/java/com/printops/demo/controller/PrinterController.java', 'w') as f:
    f.writelines(lines)
