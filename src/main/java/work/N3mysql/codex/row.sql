DROP TABLE IF EXISTS d12_employees;
CREATE TABLE d12_employees (
                               emp_id INT PRIMARY KEY,
                               dept_id INT,
                               name VARCHAR(30),
                               salary INT
);
INSERT INTO d12_employees VALUES
                              (1,10,'A',100),(2,10,'B',120),(3,10,'C',120),(4,10,'D',90),
                              (5,20,'E',200),(6,20,'F',180);

SELECT
    dept_id, name, salary,
    ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC, emp_id ASC) AS rn
FROM d12_employees;

select * from (select dept_id,
                      name,
                      salary,
                      row_number() over (partition by dept_id order by salary desc, emp_id) as rn
               from d12_employees) as t where t.rn <= 2;